package com.flowfoundation.wallet.manager.key

import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.AccountWalletManager
import com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.page.restore.keystore.PrivateKeyStoreCryptoProvider
import com.flowfoundation.wallet.wallet.Wallet
import com.flow.wallet.CryptoProvider
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.wallet.KeyWallet
import com.flow.wallet.wallet.WalletFactory
import com.flow.wallet.errors.WalletError
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.utils.error.AccountError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import org.onflow.flow.ChainId
import org.onflow.flow.models.HashingAlgorithm
import org.onflow.flow.models.SigningAlgorithm
import kotlinx.coroutines.runBlocking
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.HashMap
import com.flowfoundation.wallet.utils.readWalletPassword
import com.flow.wallet.storage.StorageProtocol

object CryptoProviderManager {

    private var cryptoProvider: CryptoProvider? = null
    private const val TAG = "CryptoProviderManager"

    fun getCurrentCryptoProvider(): CryptoProvider? {
        if (cryptoProvider == null) {
            logd(TAG, "getCurrentCryptoProvider: Cache miss, generating new provider.")
            cryptoProvider = generateAccountCryptoProvider(AccountManager.get())
        }
        return cryptoProvider
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun generateAccountCryptoProvider(account: Account?): CryptoProvider? {
        if (account == null) {
            logd(TAG, "generateAccountCryptoProvider: Input account is null.")
            return null
        }
        logd(TAG, "generateAccountCryptoProvider: Generating for account: ${account.userInfo.username}, isActive: ${account.isActive}, prefix: ${account.prefix}, hasKeystore: ${!account.keyStoreInfo.isNullOrBlank()}")

        val storage = getStorage()

        return try {
            // Handle keystore-based accounts
            if (!account.keyStoreInfo.isNullOrBlank()) {
                logd(TAG, "  Branch: Keystore-based account. Info (first 100 chars): ${account.keyStoreInfo!!.take(100)}")
                val provider = PrivateKeyStoreCryptoProvider(account.keyStoreInfo!!)
                // Log the algorithms the provider determined from the keystore info
                logd(TAG, "  Keystore-based: Provider initialized with actual signAlgo: ${provider.getSignatureAlgorithm()}, actual hashAlgo: ${provider.getHashAlgorithm()} (from keystore info)")
                return provider
            }
            // Handle prefix-based accounts
            else if (!account.prefix.isNullOrBlank()) {
                logd(TAG, "  Branch: Prefix-based account (prefix: ${account.prefix}).")
                
                // Check if this is a multi-restore account
                val isMultiRestoreAccount = try {
                    val pref = readWalletPassword()
                    val passwordMap: HashMap<String, String> = if (pref.isBlank()) {
                        HashMap<String, String>()
                    } else {
                        Gson().fromJson<HashMap<String, String>>(pref, object : TypeToken<HashMap<String, String>>() {}.type)
                    }
                    val multiRestoreCount = passwordMap["multi_restore_count"]?.toIntOrNull() ?: 0
                    val multiRestoreAddress = passwordMap["multi_restore_address"] ?: ""
                    
                    logd(TAG, "  Multi-restore check: count=$multiRestoreCount, address=$multiRestoreAddress, account=${account.wallet?.walletAddress()}")
                    multiRestoreCount > 0 && multiRestoreAddress == account.wallet?.walletAddress()
                } catch (e: Exception) {
                    logd(TAG, "  Multi-restore check failed: ${e.message}")
                    false
                }
                
                if (isMultiRestoreAccount) {
                    logd(TAG, "  MULTI-RESTORE ACCOUNT DETECTED: Using backup crypto provider for transactions")
                    
                    // Load mnemonics from stored multi-restore data
                    val pref = readWalletPassword()
                    val passwordMap: HashMap<String, String> = Gson().fromJson<HashMap<String, String>>(pref, object : TypeToken<HashMap<String, String>>() {}.type)
                    
                    val mnemonics = mutableListOf<String>()
                    val multiRestoreCount = passwordMap["multi_restore_count"]?.toIntOrNull() ?: 0
                    for (i in 0 until multiRestoreCount) {
                        passwordMap["multi_restore_$i"]?.let { mnemonic -> mnemonics.add(mnemonic) }
                    }
                    
                    logd(TAG, "  Multi-restore: Found ${mnemonics.size} stored mnemonics")
                    
                    if (mnemonics.isNotEmpty()) {
                        // Create backup crypto providers and find one that matches an on-chain key
                        val providers = mnemonics.map { mnemonic ->
                            val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, storage)
                            val wallet = WalletFactory.createKeyWallet(seedPhraseKey, setOf(ChainId.Mainnet, ChainId.Testnet), storage) as KeyWallet
                            val words = mnemonic.split(" ")
                            if (words.size == 15) {
                                BackupCryptoProvider(seedPhraseKey, wallet)
                            } else {
                                HDWalletCryptoProvider(seedPhraseKey)
                            }
                        }
                        
                        // Find the provider that matches an on-chain key
                        val accountAddress = account.wallet?.walletAddress()
                        if (accountAddress != null) {
                            try {
                                val onChainAccount = runBlocking { FlowCadenceApi.getAccount(accountAddress) }
                                logd(TAG, "  Multi-restore: Fetched on-chain account with ${onChainAccount.keys?.size} keys")
                                
                                for (provider in providers) {
                                    val providerPublicKey = provider.getPublicKey()
                                    val matchingKey = onChainAccount.keys?.find { accountKey ->
                                        val accountPubKey = accountKey.publicKey.removePrefix("0x").lowercase()
                                        val providerPubKey = providerPublicKey.lowercase()
                                        val providerPubKeyStripped = if (providerPubKey.startsWith("04") && providerPubKey.length == 130) {
                                            providerPubKey.substring(2)
                                        } else {
                                            providerPubKey
                                        }
                                        accountPubKey == providerPubKey || accountPubKey == providerPubKeyStripped
                                    }
                                    
                                    if (matchingKey != null) {
                                        logd(TAG, "  Multi-restore: Found matching provider for key index ${matchingKey.index} with algorithm ${matchingKey.signingAlgorithm}")
                                        return provider
                                    }
                                }
                                
                                logd(TAG, "  Multi-restore: No matching provider found, using first provider as fallback")
                                return providers.first()
                            } catch (e: Exception) {
                                logd(TAG, "  Multi-restore: Error finding matching provider: ${e.message}, using first provider")
                                return providers.first()
                            }
                        } else {
                            logd(TAG, "  Multi-restore: No account address, using first provider")
                            return providers.first()
                        }
                    } else {
                        logd(TAG, "  Multi-restore: No mnemonics found, falling back to standard prefix handling")
                    }
                }
                
                // Standard prefix-based account handling (for non-multi-restore accounts)
                logd(TAG, "  Standard prefix-based account handling")
                val keyId = "prefix_key_${account.prefix}"
                val privateKey = try {
                    PrivateKey.get(keyId, account.prefix!!, storage)
                } catch (e: Exception) {
                    loge(TAG, "CRITICAL ERROR: Failed to load stored private key for prefix ${account.prefix}: ${e.message}")
                    return null
                }
                val wallet = WalletFactory.createKeyWallet(privateKey, setOf(ChainId.Mainnet, ChainId.Testnet), storage) as KeyWallet
                
                // Determine the correct signing algorithm for this private key
                val currentProviderPublicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString() 
                    ?: privateKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()
                var determinedSigningAlgorithm = SigningAlgorithm.ECDSA_P256 // Default
                logd(TAG, "  Prefix-based: currentProviderPublicKey from local private key: $currentProviderPublicKey")
                logd(TAG, "  Prefix-based: account.wallet?.walletAddress() for on-chain lookup: ${account.wallet?.walletAddress()}")

                if (currentProviderPublicKey != null && account.wallet?.walletAddress() != null) {
                    try {
                        val onChainAccount = runBlocking { FlowCadenceApi.getAccount(account.wallet!!.walletAddress()!!) }
                        logd(TAG, "  Prefix-based: Fetched on-chain account for ${account.wallet!!.walletAddress()!!}: ${onChainAccount.address}")
                        val onChainKey = onChainAccount.keys?.find { acctKey ->
                            val acctPubKeyHex = acctKey.publicKey.removePrefix("0x").lowercase()
                            val providerPubKeyHex = currentProviderPublicKey.removePrefix("0x").lowercase()
                            // Handle potential "04" prefix for uncompressed keys from some secp256k1 derivations
                            val providerPubKeyStripped = if (providerPubKeyHex.startsWith("04") && providerPubKeyHex.length == 130) providerPubKeyHex.substring(2) else providerPubKeyHex
                            val isMatch = acctPubKeyHex == providerPubKeyHex || acctPubKeyHex == providerPubKeyStripped
                            if (isMatch) logd(TAG, "  Prefix-based: Matched on-chain key: index=${acctKey.index}, pub=${acctKey.publicKey}, signAlgo=${acctKey.signingAlgorithm}")
                            isMatch
                        }
                        if (onChainKey != null) {
                            determinedSigningAlgorithm = onChainKey.signingAlgorithm
                            logd(TAG, "  Prefix-based: Successfully determined on-chain signing algorithm: $determinedSigningAlgorithm for key index ${onChainKey.index}")
                        } else {
                            logd(TAG, "  Prefix-based: Could NOT find matching on-chain key for $currentProviderPublicKey. Using default signing algorithm: $determinedSigningAlgorithm")
                        }
                    } catch (e: Exception) {
                        loge(TAG, "  Prefix-based account: Error fetching on-chain key details to determine signing algorithm: ${e.message}. Using default.")
                    }
                } else {
                    logd(TAG, "  Prefix-based: Missing local public key or wallet address to determine on-chain signing algorithm. Using default: $determinedSigningAlgorithm")
                }
                
                // Also determine the hashing algorithm from the on-chain key
                val determinedHashingAlgorithm = if (currentProviderPublicKey != null && account.wallet?.walletAddress() != null) {
                    try {
                        val onChainAccount = runBlocking { FlowCadenceApi.getAccount(account.wallet!!.walletAddress()!!) }
                        val onChainKey = onChainAccount.keys?.find { acctKey ->
                            val acctPubKeyHex = acctKey.publicKey.removePrefix("0x").lowercase()
                            val providerPubKeyHex = currentProviderPublicKey.removePrefix("0x").lowercase()
                            val providerPubKeyStripped = if (providerPubKeyHex.startsWith("04") && providerPubKeyHex.length == 130) providerPubKeyHex.substring(2) else providerPubKeyHex
                            acctPubKeyHex == providerPubKeyHex || acctPubKeyHex == providerPubKeyStripped
                        }
                        onChainKey?.hashingAlgorithm
                    } catch (e: Exception) {
                        logd(TAG, "  Prefix-based: Error fetching hashing algorithm: ${e.message}")
                        null
                    }
                } else {
                    null
                }
                
                logd(TAG, "  Prefix-based: Instantiating PrivateKeyCryptoProvider with determined algorithms: signing=$determinedSigningAlgorithm, hashing=${determinedHashingAlgorithm ?: "default"}")
                PrivateKeyCryptoProvider(privateKey, wallet, determinedSigningAlgorithm, determinedHashingAlgorithm)
            }
            // Handle active accounts (typically uses global mnemonic)
            else if (account.isActive) {
                val currentGlobalMnemonic = Wallet.store().mnemonic() // Get current global mnemonic
                logd(TAG, "  Branch: Active account. Using Wallet.store().mnemonic(): '$currentGlobalMnemonic'")
                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = currentGlobalMnemonic,
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )
                val wallet = WalletFactory.createKeyWallet(seedPhraseKey, setOf(ChainId.Mainnet, ChainId.Testnet), storage) as KeyWallet
                // Signing algorithm determination logic (omitted for brevity, assumed correct from previous state)
                val accountKeys = account.wallet?.walletAddress()
                    ?.let { runBlocking { FlowCadenceApi.getAccount(it).keys } }
                val signingAlgorithm = if (accountKeys?.any { it.signingAlgorithm == SigningAlgorithm.ECDSA_secp256k1 } == true) {
                    SigningAlgorithm.ECDSA_secp256k1
                } else {
                    SigningAlgorithm.ECDSA_P256
                }
                // Get the hashing algorithm from the on-chain key as well
                val hashingAlgorithm = accountKeys?.find { it.signingAlgorithm == signingAlgorithm }?.hashingAlgorithm
                logd(TAG, "  Active account using determined algorithms: signing=$signingAlgorithm, hashing=${hashingAlgorithm ?: "default"}")
                BackupCryptoProvider(seedPhraseKey, wallet, signingAlgorithm, hashingAlgorithm)
            }
            // Handle inactive accounts (may use wallet-specific mnemonic)
            else {
                logd(TAG, "  Branch: Inactive account.")
                val existingWallet = AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
                if (existingWallet == null) {
                    loge(TAG, "  Inactive account: Failed to get existing HDWallet by UID: ${account.wallet?.id}")
                    ErrorReporter.reportWithMixpanel(AccountError.GET_WALLET_FAILED)
                    return null
                }
                val inactiveMnemonic = (existingWallet as BackupCryptoProvider).getMnemonic()
                logd(TAG, "  Inactive account: Using mnemonic from existing HDWallet: '$inactiveMnemonic'")
                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = inactiveMnemonic,
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )
                val wallet = WalletFactory.createKeyWallet(seedPhraseKey, setOf(ChainId.Mainnet, ChainId.Testnet), storage) as KeyWallet
                // Signing algorithm determination logic (omitted for brevity, assumed correct from previous state)
                val accountKeys = account.wallet?.walletAddress()
                    ?.let { runBlocking { FlowCadenceApi.getAccount(it).keys } }
                val signingAlgorithm = if (accountKeys?.any { it.signingAlgorithm == SigningAlgorithm.ECDSA_secp256k1 } == true) {
                    SigningAlgorithm.ECDSA_secp256k1
                } else {
                    SigningAlgorithm.ECDSA_P256
                }
                // Get the hashing algorithm from the on-chain key as well
                val hashingAlgorithm = accountKeys?.find { it.signingAlgorithm == signingAlgorithm }?.hashingAlgorithm
                logd(TAG, "  Inactive account using determined algorithms: signing=$signingAlgorithm, hashing=${hashingAlgorithm ?: "default"}")
                BackupCryptoProvider(seedPhraseKey, wallet, signingAlgorithm, hashingAlgorithm)
            }
        } catch (e: WalletError) {
            logd(TAG, "Wallet error during provider generation: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.WALLET_ERROR, e)
            null
        } catch (e: Exception) {
            logd(TAG, "Unexpected error during provider generation: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.UNEXPECTED_ERROR, e)
            null
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getSwitchAccountCryptoProvider(account: Account): CryptoProvider? {
        val storage = getStorage()
        
        logd("CryptoProviderManager", "getSwitchAccountCryptoProvider called for account: ${account.userInfo.username}")
        logd("CryptoProviderManager", "  keyStoreInfo present: ${!account.keyStoreInfo.isNullOrBlank()}")
        logd("CryptoProviderManager", "  prefix present: ${!account.prefix.isNullOrBlank()}")
        logd("CryptoProviderManager", "  wallet ID: ${account.wallet?.id}")

        return try {
            // Handle keystore-based accounts
            if (account.keyStoreInfo.isNullOrBlank().not()) {
                logd("CryptoProviderManager", "Creating PrivateKeyStoreCryptoProvider for keystore-based account")
                logd("CryptoProviderManager", "  keyStoreInfo length: ${account.keyStoreInfo!!.length}")
                PrivateKeyStoreCryptoProvider(account.keyStoreInfo!!)
            }

            // Handle prefix-based accounts
            else if (account.prefix.isNullOrBlank().not()) {
                logd("CryptoProviderManager", "Creating PrivateKeyCryptoProvider for prefix-based account")
                logd("CryptoProviderManager", "  prefix: ${account.prefix}")
                
                // Load the stored private key using the prefix-based ID
                val keyId = "prefix_key_${account.prefix}"
                val privateKey = try {
                    PrivateKey.get(keyId, account.prefix!!, storage)
                } catch (e: Exception) {
                    loge("CryptoProviderManager", "CRITICAL ERROR: Failed to load stored private key for switch account prefix ${account.prefix}: ${e.message}")
                    loge("CryptoProviderManager", "Cannot proceed without the stored key as it would create a different account")
                    return null // Return null instead of creating a new key
                }
                
                // Create a proper KeyWallet directly with the PrivateKey
                val wallet = WalletFactory.createKeyWallet(
                    privateKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet
                
                // Determine the correct signing algorithm for this private key from on-chain data
                val currentProviderPublicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString() 
                    ?: privateKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()
                var determinedSigningAlgorithm = SigningAlgorithm.ECDSA_P256 // Default
                var determinedHashingAlgorithm: HashingAlgorithm? = null
                
                if (currentProviderPublicKey != null && account.wallet?.walletAddress() != null) {
                    try {
                        val onChainAccount = runBlocking { FlowCadenceApi.getAccount(account.wallet!!.walletAddress()!!) }
                        logd("CryptoProviderManager", "Fetched on-chain account for ${account.wallet!!.walletAddress()!!}")
                        val onChainKey = onChainAccount.keys?.find { acctKey ->
                            val acctPubKeyHex = acctKey.publicKey.removePrefix("0x").lowercase()
                            val providerPubKeyHex = currentProviderPublicKey.removePrefix("0x").lowercase()
                            val providerPubKeyStripped = if (providerPubKeyHex.startsWith("04") && providerPubKeyHex.length == 130) providerPubKeyHex.substring(2) else providerPubKeyHex
                            val isMatch = acctPubKeyHex == providerPubKeyHex || acctPubKeyHex == providerPubKeyStripped
                            if (isMatch) logd("CryptoProviderManager", "Matched on-chain key: index=${acctKey.index}, signAlgo=${acctKey.signingAlgorithm}, hashAlgo=${acctKey.hashingAlgorithm}")
                            isMatch
                        }
                        if (onChainKey != null) {
                            determinedSigningAlgorithm = onChainKey.signingAlgorithm
                            determinedHashingAlgorithm = onChainKey.hashingAlgorithm
                            logd("CryptoProviderManager", "Successfully determined on-chain algorithms: signing=$determinedSigningAlgorithm, hashing=$determinedHashingAlgorithm")
                        } else {
                            logd("CryptoProviderManager", "Could NOT find matching on-chain key. Using defaults.")
                        }
                    } catch (e: Exception) {
                        loge("CryptoProviderManager", "Error fetching on-chain key details: ${e.message}. Using defaults.")
                    }
                }
                
                // For prefix-based accounts, we use PrivateKeyCryptoProvider instead of BackupCryptoProvider
                PrivateKeyCryptoProvider(privateKey, wallet, determinedSigningAlgorithm, determinedHashingAlgorithm)
            }

            // Handle other accounts (mnemonic-based)
            else {
                logd("CryptoProviderManager", "Creating BackupCryptoProvider for mnemonic-based account")
                val existingWallet = AccountWalletManager.getHDWalletByUID(account.wallet?.id ?: "")
                if (existingWallet == null) {
                    loge("CryptoProviderManager", "Failed to get existing wallet for account ${account.userInfo.username}")
                    ErrorReporter.reportWithMixpanel(AccountError.GET_WALLET_FAILED)
                    return null
                }

                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = (existingWallet as BackupCryptoProvider).getMnemonic(),
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )
                // Create a proper KeyWallet
                val wallet = WalletFactory.createKeyWallet(
                    seedPhraseKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet
                
                // Determine signing and hashing algorithms from on-chain data
                val accountKeys = account.wallet?.walletAddress()
                    ?.let { runBlocking { FlowCadenceApi.getAccount(it).keys } }
                val signingAlgorithm = if (accountKeys?.any { it.signingAlgorithm == SigningAlgorithm.ECDSA_secp256k1 } == true) {
                    SigningAlgorithm.ECDSA_secp256k1
                } else {
                    SigningAlgorithm.ECDSA_P256
                }
                // Get the hashing algorithm from the on-chain key as well
                val hashingAlgorithm = accountKeys?.find { it.signingAlgorithm == signingAlgorithm }?.hashingAlgorithm
                logd("CryptoProviderManager", "Mnemonic-based account using determined algorithms: signing=$signingAlgorithm, hashing=${hashingAlgorithm ?: "default"}")
                
                BackupCryptoProvider(seedPhraseKey, wallet, signingAlgorithm, hashingAlgorithm)
            }
        } catch (e: WalletError) {
            loge("CryptoProviderManager", "Wallet error: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.WALLET_ERROR, e)
            null
        } catch (e: Exception) {
            loge("CryptoProviderManager", "Unexpected error: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.UNEXPECTED_ERROR, e)
            null
        }
    }

    fun getSwitchAccountCryptoProvider(switchAccount: LocalSwitchAccount): CryptoProvider? {
        val storage = getStorage()

        return try {
            // Handle prefix-based accounts
            if (switchAccount.prefix.isNullOrBlank().not()) {
                // Load the stored private key using the prefix-based ID
                val keyId = "prefix_key_${switchAccount.prefix}"
                val privateKey = try {
                    PrivateKey.get(keyId, switchAccount.prefix!!, storage)
                } catch (e: Exception) {
                    loge("CryptoProviderManager", "CRITICAL ERROR: Failed to load stored private key for local switch account prefix ${switchAccount.prefix}: ${e.message}")
                    loge("CryptoProviderManager", "Cannot proceed without the stored key as it would create a different account")
                    return null // Return null instead of creating a new key
                }
                
                // Create a proper KeyWallet directly with the PrivateKey
                val wallet = WalletFactory.createKeyWallet(
                    privateKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet
                
                // For prefix-based accounts, we use PrivateKeyCryptoProvider instead of BackupCryptoProvider
                PrivateKeyCryptoProvider(privateKey, wallet)
            }

            // Handle other accounts
            else {
                val existingWallet = AccountWalletManager.getHDWalletByUID(switchAccount.userId ?: "")
                if (existingWallet == null) {
                    loge("CryptoProviderManager", "Failed to get existing wallet for switch account ${switchAccount.username}")
                    ErrorReporter.reportWithMixpanel(AccountError.GET_WALLET_FAILED)
                    return null
                }

                val seedPhraseKey = SeedPhraseKey(
                    mnemonicString = (existingWallet as BackupCryptoProvider).getMnemonic(),
                    passphrase = "",
                    derivationPath = "m/44'/539'/0'/0/0",
                    keyPair = null,
                    storage = storage
                )
                // Create a proper KeyWallet
                val wallet = WalletFactory.createKeyWallet(
                    seedPhraseKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet
                BackupCryptoProvider(seedPhraseKey, wallet)
            }
        } catch (e: WalletError) {
            loge("CryptoProviderManager", "Wallet error: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.WALLET_ERROR, e)
            null
        } catch (e: Exception) {
            loge("CryptoProviderManager", "Unexpected error: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.UNEXPECTED_ERROR, e)
            null
        }
    }

    fun clear() {
        cryptoProvider = null
    }

    /**
     * Create a SeedPhraseKey with properly initialized keyPair
     * This fixes the "Signing key is empty or not available" error
     */
    @OptIn(ExperimentalStdlibApi::class)
    private fun createSeedPhraseKeyWithKeyPair(mnemonic: String, storage: StorageProtocol): SeedPhraseKey {
        logd(TAG, "Creating SeedPhraseKey with proper keyPair initialization")
        
        try {
            // Create a simple dummy KeyPair to pass the null check in sign()
            // The actual signing uses hdWallet.getKeyByCurve() internally, not this keyPair
            val keyGenerator = java.security.KeyPairGenerator.getInstance("EC")
            keyGenerator.initialize(256)
            val dummyKeyPair = keyGenerator.generateKeyPair()
            
            logd(TAG, "Created dummy KeyPair for null check")
            
            // Create SeedPhraseKey with the dummy keyPair
            val seedPhraseKey = SeedPhraseKey(
                mnemonicString = mnemonic,
                passphrase = "",
                derivationPath = "m/44'/539'/0'/0/0",
                keyPair = dummyKeyPair,
                storage = storage
            )
            
            // Verify that the SeedPhraseKey can generate keys using its internal hdWallet
            try {
                val publicKey = seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)
                if (publicKey == null) {
                    throw RuntimeException("SeedPhraseKey failed to generate public key")
                }
                logd(TAG, "SeedPhraseKey successfully verified with public key: ${publicKey.toHexString().take(20)}...")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "SeedPhraseKey verification failed", e)
                throw RuntimeException("SeedPhraseKey verification failed", e)
            }
            
            return seedPhraseKey
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to create SeedPhraseKey", e)
            throw RuntimeException("Failed to create SeedPhraseKey with proper keyPair", e)
        }
    }
}