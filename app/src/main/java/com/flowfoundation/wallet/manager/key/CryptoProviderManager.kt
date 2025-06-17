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
import com.flow.wallet.storage.FileSystemStorage
import com.flowfoundation.wallet.manager.wallet.WalletManager

/**
 * A CryptoProvider that handles multi-restore accounts by combining multiple backup providers
 * to meet the signature weight threshold requirements
 */
class MultiRestoreCryptoProvider(
    private val providers: List<BackupCryptoProvider>,
    private val primaryProvider: BackupCryptoProvider
) : CryptoProvider {
    
    private val TAG = "MultiRestoreCryptoProvider"
    
    init {
        logd(TAG, "Initialized with ${providers.size} backup providers")
        providers.forEachIndexed { index, provider ->
            logd(TAG, "Provider $index: weight=${provider.getKeyWeight()}, algo=${provider.getSignatureAlgorithm()}")
        }
    }
    
    override fun getPublicKey(): String {
        return primaryProvider.getPublicKey()
    }
    
    override suspend fun getUserSignature(jwt: String): String {
        return primaryProvider.getUserSignature(jwt)
    }
    
    override suspend fun signData(data: ByteArray): String {
        return primaryProvider.signData(data)
    }
    
    override fun getSigner(hashingAlgorithm: HashingAlgorithm): org.onflow.flow.models.Signer {
        return primaryProvider.getSigner(hashingAlgorithm)
    }
    
    override fun getHashAlgorithm(): HashingAlgorithm {
        return primaryProvider.getHashAlgorithm()
    }
    
    override fun getSignatureAlgorithm(): SigningAlgorithm {
        return primaryProvider.getSignatureAlgorithm()
    }
    
    override fun getKeyWeight(): Int {
        // Return the total weight of all providers
        return providers.sumOf { it.getKeyWeight() }
    }
    
    /**
     * Get all backup providers for multi-signature transactions
     */
    fun getAllProviders(): List<BackupCryptoProvider> {
        return providers
    }
    
    /**
     * Get the primary provider (first one, used for non-multi-sig operations)
     */
    fun getPrimaryProvider(): BackupCryptoProvider {
        return primaryProvider
    }
}

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
                    logd(TAG, "  MULTI-RESTORE ACCOUNT DETECTED: Creating multi-signature crypto provider")
                    
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
                        val accountAddress = account.wallet?.walletAddress()
                        if (accountAddress != null) {
                            try {
                                val onChainAccount = runBlocking { FlowCadenceApi.getAccount(accountAddress) }
                                logd(TAG, "  Multi-restore: Fetched on-chain account with ${onChainAccount.keys?.size} keys")
                                
                                // Create providers from all mnemonics and detect their algorithms
                                val providers = mnemonics.mapNotNull { mnemonic ->
                                    try {
                                        val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, storage)
                                        
                                        // Detect algorithms by matching against on-chain keys
                                        val detectedAlgorithms = detectAlgorithmsForProvider(seedPhraseKey, onChainAccount.keys?.toList() ?: emptyList())
                                        
                                        val words = mnemonic.split(" ")
                                        val provider = if (words.size == 15) {
                                            BackupCryptoProvider(seedPhraseKey, null, detectedAlgorithms.signingAlgorithm, detectedAlgorithms.hashingAlgorithm)
                                        } else {
                                            HDWalletCryptoProvider(seedPhraseKey, detectedAlgorithms.signingAlgorithm, detectedAlgorithms.hashingAlgorithm)
                                        }
                                        
                                        // Verify this provider has a matching key on-chain
                                        val providerPublicKey = provider.getPublicKey()
                                        val matchingKey = onChainAccount.keys?.find { accountKey ->
                                            isKeyMatchRobust(providerPublicKey, accountKey.publicKey) && !accountKey.revoked
                                        }
                                        
                                        if (matchingKey != null) {
                                            logd(TAG, "  Multi-restore: Provider matches on-chain key index ${matchingKey.index} with weight ${matchingKey.weight}")
                                            provider as BackupCryptoProvider // Cast HDWalletCryptoProvider to BackupCryptoProvider if needed
                                        } else {
                                            logd(TAG, "  Multi-restore: Provider for mnemonic does not match any on-chain key, skipping")
                                            null
                                        }
                                    } catch (e: Exception) {
                                        logd(TAG, "  Multi-restore: Error creating provider for mnemonic: ${e.message}")
                                        null
                                    }
                                }
                                
                                if (providers.isNotEmpty()) {
                                    val totalWeight = providers.sumOf { it.getKeyWeight() }
                                    logd(TAG, "  Multi-restore: Created ${providers.size} providers with total weight $totalWeight")
                                    
                                    return MultiRestoreCryptoProvider(providers, providers.first())
                                } else {
                                    logd(TAG, "  Multi-restore: No valid providers created, falling back to standard prefix handling")
                                }
                            } catch (e: Exception) {
                                logd(TAG, "  Multi-restore: Error processing multi-restore account: ${e.message}, using standard prefix handling")
                            }
                        } else {
                            logd(TAG, "  Multi-restore: No account address, using standard prefix handling")
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

                // Try to get on-chain account information to determine the correct signing algorithm
                try {
                    val accountAddress = account.wallet?.walletAddress()
                    if (accountAddress != null) {
                        val onChainAccount = runBlocking { FlowCadenceApi.getAccount(accountAddress) }
                        val onChainKeys = onChainAccount.keys?.toList() ?: emptyList()
                        
                        // Test both signing algorithms to see which one matches the on-chain key
                        val ecdsaP256PublicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()
                        val ecdsaSecp256k1PublicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()
                        
                        logd(TAG, "  Prefix-based: Testing key matching...")
                        logd(TAG, "    ECDSA_P256 key: ${ecdsaP256PublicKey?.take(20)}...")
                        logd(TAG, "    ECDSA_secp256k1 key: ${ecdsaSecp256k1PublicKey?.take(20)}...")
                        
                        // Find matching on-chain key
                        var matchedKey: org.onflow.flow.models.AccountPublicKey? = null
                        
                        if (ecdsaP256PublicKey != null) {
                            matchedKey = onChainKeys.find { onChainKey ->
                                val match = isKeyMatchRobust(ecdsaP256PublicKey, onChainKey.publicKey)
                                if (match) {
                                    logd(TAG, "    ✓ ECDSA_P256 key matches on-chain key index ${onChainKey.index}")
                                    determinedSigningAlgorithm = SigningAlgorithm.ECDSA_P256
                                }
                                match
                            }
                        }
                        
                        if (matchedKey == null && ecdsaSecp256k1PublicKey != null) {
                            matchedKey = onChainKeys.find { onChainKey ->
                                val match = isKeyMatchRobust(ecdsaSecp256k1PublicKey, onChainKey.publicKey)
                                if (match) {
                                    logd(TAG, "    ✓ ECDSA_secp256k1 key matches on-chain key index ${onChainKey.index}")
                                    determinedSigningAlgorithm = SigningAlgorithm.ECDSA_secp256k1
                                }
                                match
                            }
                        }
                        
                        if (matchedKey != null) {
                            logd(TAG, "  Prefix-based: Found matching on-chain key with algorithm: $determinedSigningAlgorithm")
                            logd(TAG, "  Prefix-based: On-chain key hashing algorithm: ${matchedKey.hashingAlgorithm}")
                            
                            // Create the provider with the correct algorithms
                            return PrivateKeyCryptoProvider(privateKey, wallet, determinedSigningAlgorithm, matchedKey.hashingAlgorithm)
                        } else {
                            logd(TAG, "  Prefix-based: Could NOT find matching on-chain key for ${account.prefix}. Using default signing algorithm: $determinedSigningAlgorithm")
                        }
                    } else {
                        logd(TAG, "  Prefix-based: No account address available for on-chain key lookup")
                    }
                } catch (e: Exception) {
                    logd(TAG, "  Prefix-based: Error during on-chain key lookup: ${e.message}. Using default algorithms.")
                }
                
                // Create provider with determined (or default) signing algorithm  
                return PrivateKeyCryptoProvider(privateKey, wallet, determinedSigningAlgorithm)
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
                    ?: throw RuntimeException("SeedPhraseKey failed to generate public key")
                logd(TAG, "SeedPhraseKey successfully verified with public key: ${publicKey.toHexString().take(20)}...")
            } catch (e: Exception) {
                throw RuntimeException("SeedPhraseKey verification failed", e)
            }
            
            return seedPhraseKey
            
        } catch (e: Exception) {
            throw RuntimeException("Failed to create SeedPhraseKey with proper keyPair", e)
        }
    }

    /**
     * Data class to hold detected algorithm pair
     */
    private data class AlgorithmPair(
        val signingAlgorithm: SigningAlgorithm,
        val hashingAlgorithm: HashingAlgorithm
    )
    
    /**
     * Detect algorithms for a provider by testing against on-chain keys
     */
    @OptIn(ExperimentalStdlibApi::class)
    private fun detectAlgorithmsForProvider(
        seedPhraseKey: SeedPhraseKey, 
        onChainKeys: List<org.onflow.flow.models.AccountPublicKey>
    ): AlgorithmPair {
        val signingAlgorithms = listOf(SigningAlgorithm.ECDSA_secp256k1, SigningAlgorithm.ECDSA_P256)
        
        // Test each signing algorithm to find matching public key
        for (signingAlgorithm in signingAlgorithms) {
            try {
                val publicKey = seedPhraseKey.publicKey(signingAlgorithm)?.toHexString() ?: continue
                
                // Check if this key matches any on-chain key
                val matchedKey = onChainKeys.find { onChainKey ->
                    !onChainKey.revoked && isKeyMatchRobust(publicKey, onChainKey.publicKey)
                }
                
                if (matchedKey != null) {
                    logd(TAG, "Detected algorithms: signing=$signingAlgorithm, hashing=${matchedKey.hashingAlgorithm}")
                    return AlgorithmPair(signingAlgorithm, matchedKey.hashingAlgorithm)
                }
            } catch (e: Exception) {
                // Continue to next algorithm
            }
        }
        
        // Default fallback
        return AlgorithmPair(SigningAlgorithm.ECDSA_P256, HashingAlgorithm.SHA2_256)
    }
    
    /**
     * Robust key matching logic
     */
    private fun isKeyMatchRobust(providerPublicKey: String, onChainPublicKey: String): Boolean {
        val providerRaw = providerPublicKey.removePrefix("0x").lowercase()
        val onChainRaw = onChainPublicKey.removePrefix("0x").lowercase()
        
        val providerStripped = if (providerRaw.startsWith("04") && providerRaw.length == 130) providerRaw.substring(2) else providerRaw
        val onChainStripped = if (onChainRaw.startsWith("04") && onChainRaw.length == 130) onChainRaw.substring(2) else onChainRaw
        val providerWith04 = if (!providerRaw.startsWith("04") && providerRaw.length == 128) "04$providerRaw" else providerRaw
        val onChainWith04 = if (!onChainRaw.startsWith("04") && onChainRaw.length == 128) "04$onChainRaw" else onChainRaw
        
        return onChainRaw == providerRaw || 
               onChainRaw == providerStripped || 
               onChainStripped == providerRaw || 
               onChainStripped == providerStripped ||
               onChainRaw == providerWith04 ||
               onChainWith04 == providerRaw ||
               onChainWith04 == providerStripped ||
               onChainStripped == providerWith04
    }
}