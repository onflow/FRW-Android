package com.flowfoundation.wallet.manager.key

import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.AccountWalletManager
import com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.page.restore.keystore.PrivateKeyStoreCryptoProvider
import com.flow.wallet.CryptoProvider
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
import com.flowfoundation.wallet.manager.account.HardwareBackedKeyException
import com.flowfoundation.wallet.manager.account.firstFlowWalletAddress
import com.flowfoundation.wallet.manager.key.storage.KeyStorageManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.wallet.DERIVATION_PATH
import com.flowfoundation.wallet.wallet.Wallet
import com.flow.wallet.keys.PrivateKey
import org.onflow.flow.models.toHexString

object CryptoProviderManager {

    private var cryptoProvider: CryptoProvider? = null
    private const val TAG = "CryptoProviderManager"

    fun getCurrentCryptoProvider(): CryptoProvider? {
        // Check if current provider is valid
        val isValid = cryptoProvider?.let {
            try {
                // Validate that the provider is properly initialized
                it.getPublicKey().isNotBlank() &&
                it.getPublicKey() != "0x" &&
                it.getPublicKey().length >= 64 // Minimum valid key length
            } catch (e: Exception) {
                loge(TAG, "CryptoProvider validation failed: ${e.message}")
                false
            }
        } ?: false

        if (!isValid) {
            logd(TAG, "getCurrentCryptoProvider: Cache miss or invalid provider, generating new provider.")
            cryptoProvider = generateAccountCryptoProvider(AccountManager.get())
        }
        return cryptoProvider
    }

    fun generateAccountCryptoProvider(account: Account?): CryptoProvider? {
        return try {
            if (account == null) {
                loge(TAG, "Cannot generate crypto provider: account is null")
                return null
            }

            val provider = createCryptoProviderForAccount(account)
            if (provider != null) {
                // Validate the generated provider before returning
                val publicKey = try {
                    provider.getPublicKey()
                } catch (e: Exception) {
                    loge(TAG, "Failed to get public key from provider: ${e.message}")
                    return null
                }

                if (publicKey.isBlank() || publicKey == "0x" || publicKey.length < 64) {
                    loge(TAG, "Generated provider has invalid public key: $publicKey")
                    ErrorReporter.reportWithMixpanel(AccountError.INVALID_PUBLIC_KEY)
                    return null
                }
                logd(TAG, "Successfully generated valid crypto provider for ${account.userInfo.username}")
                provider
            } else {
                loge(TAG, "Failed to create crypto provider for account ${account.userInfo.username}")
                null
            }
        } catch (e: Exception) {
            loge(TAG, "Exception generating crypto provider for ${account!!.userInfo.username}: ${e.message}")
            ErrorReporter.reportWithMixpanel(AccountError.GENERATE_PROVIDER_FAILED, e)
            null
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun createCryptoProviderForAccount(account: Account): CryptoProvider? {
        val storage = getStorage()

        logd(TAG, "generateAccountCryptoProvider: Generating for account: ${account.userInfo.username}, isActive: ${account.isActive}, hasKeystore: ${!account.keyStoreInfo.isNullOrBlank()}")

        return try {
            // --- New independent key storage (checked first) ---
            val uid = account.wallet?.id
            if (!uid.isNullOrBlank()) {
                // SeedPhraseKey object retrieved directly – no intermediate string reconstruction
                val seedPhraseKey = KeyStorageManager.getSeedPhraseKey(uid)
                if (seedPhraseKey != null) {
                    logd(TAG, "New storage: found seed phrase key for uid: $uid")
                    return HDWalletCryptoProvider(seedPhraseKey)
                }
                // PrivateKey object retrieved directly – no intermediate string reconstruction
                val privateKey = KeyStorageManager.getPrivateKeyObject(uid)
                if (privateKey != null) {
                    logd(TAG, "New storage: found private key object for uid: $uid")
                    val keyWallet = WalletFactory.createKeyWallet(privateKey, setOf(ChainId.Mainnet, ChainId.Testnet), storage) as KeyWallet
                    val address = account.firstFlowWalletAddress() ?: ""
                    return runBlocking { createPrivateKeyCryptoProvider(privateKey, keyWallet, address) }
                }
                // AKP prefix from new storage falls through to prefix-based branch below
            }

            // Handle keystore-based accounts
            if (!account.keyStoreInfo.isNullOrBlank()) {
                logd(TAG, "  Branch: Keystore-based account. Info (first 100 chars): ${account.keyStoreInfo!!.take(100)}")
                val provider = PrivateKeyStoreCryptoProvider(account.keyStoreInfo!!)
                // Log the algorithms the provider determined from the keystore info
                logd(TAG, "  Keystore-based: Provider initialized with actual signAlgo: ${provider.getSignatureAlgorithm()}, actual hashAlgo: ${provider.getHashAlgorithm()} (from keystore info)")
                return provider
            }
            // Handle mnemonic-only accounts (cleaner architecture - no prefix, just mnemonic)
            // Check this BEFORE prefix to properly handle new RN seed phrase accounts
            else if (account.prefix.isNullOrBlank() && !account.wallet?.id.isNullOrBlank() &&
                     AccountWalletManager.hasHDWalletKeystore(account.wallet?.id ?: "")) {
                logd(TAG, "  Branch: Mnemonic-only account (cleaner architecture)")
                val userId = account.wallet?.id ?: ""
                val mnemonic = AccountWalletManager.getHDWalletMnemonicByUID(userId)
                if (mnemonic == null) {
                    loge(TAG, "  Mnemonic-only: Failed to get mnemonic by UID: $userId")
                    ErrorReporter.reportWithMixpanel(AccountError.GET_WALLET_FAILED)
                    return null
                }
                val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, getStorage())
                return HDWalletCryptoProvider(seedPhraseKey)
            }
            // Handle prefix-based accounts (legacy or hardware-backed)
            // Also check new AKP storage when account.prefix is absent
            else if (!account.prefix.isNullOrBlank() || (!uid.isNullOrBlank() && KeyStorageManager.hasAndroidKeystorePrefix(uid))) {
                val effectivePrefix = account.prefix?.takeIf { it.isNotBlank() }
                    ?: uid?.let { KeyStorageManager.getAndroidKeystorePrefix(it) }

                logd(TAG, "  Branch: Prefix-based account (effectivePrefix from ${if (account.prefix.isNullOrBlank()) "new storage" else "account"})")

                if (effectivePrefix.isNullOrBlank()) {
                    loge(TAG, "  Prefix-based: resolved prefix is blank, skipping")
                    return null
                }

                // Standard prefix-based account handling (for non-multi-restore accounts)
                logd(TAG, "  Standard prefix-based account handling")

                // Try to get the private key, handling hardware-backed keys
                val privateKey = try {
                    KeyCompatibilityManager.getPrivateKeyWithFallback(effectivePrefix, storage)
                } catch (e: HardwareBackedKeyException) {
                    loge(TAG, "Hardware-backed key detected")
                    return AndroidKeystoreCryptoProvider(e.prefix!!)
                }

                if (privateKey == null) {
                    loge(TAG, "CRITICAL ERROR: Failed to load stored private key from both new and old storage")
                    return null
                }
                val wallet = WalletFactory.createKeyWallet(privateKey, setOf(ChainId.Mainnet, ChainId.Testnet), storage) as KeyWallet

                // Determine the correct signing algorithm for this private key
                privateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()
                    ?: privateKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()
                var determinedSigningAlgorithm = SigningAlgorithm.ECDSA_P256 // Default

                // Try to get on-chain account information to determine the correct signing algorithm
                try {
                    val accountAddress = account.firstFlowWalletAddress() ?: ""
                    if (accountAddress.isNotEmpty()) {
                        val onChainAccount = runBlocking { FlowCadenceApi.getAccount(accountAddress) }
                        val onChainKeys = onChainAccount.keys?.toList() ?: emptyList()

                        // Test both signing algorithms to see which one matches the on-chain key
                        val ecdsaP256PublicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()
                        val ecdsaSecp256k1PublicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()

                        // Find matching on-chain key
                        var matchedKey: org.onflow.flow.models.AccountPublicKey? = null

                        if (ecdsaP256PublicKey != null) {
                            matchedKey = onChainKeys.find { onChainKey ->
                                val match = isKeyMatchRobust(ecdsaP256PublicKey, onChainKey.publicKey)
                                if (match) {
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
                            // Create the provider with the correct algorithms
                            return PrivateKeyCryptoProvider(privateKey, wallet, determinedSigningAlgorithm, matchedKey.hashingAlgorithm)
                        } else {
                            logd(TAG, "  Prefix-based: Could NOT find matching on-chain key for $effectivePrefix. Using default signing algorithm: $determinedSigningAlgorithm")
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
            // Handle wallet-specific mnemonic accounts
            else {
                logd(TAG, "  Branch: Legacy mnemonic account.")
                // Always look up the mnemonic by UID to avoid using the wrong account's mnemonic.
                // Using Wallet.store().wallet().mnemonic() here would return the CURRENT account's
                // mnemonic (the one being switched away from), not the target account's.
                val mnemonic = AccountWalletManager.getHDWalletMnemonicByUID(account.wallet?.id ?: "")

                if (mnemonic == null) {
                    loge(TAG, "  Inactive account: Failed to get existing HDWallet by UID: ${account.wallet?.id}")
                    ErrorReporter.reportWithMixpanel(AccountError.GET_WALLET_FAILED)
                    return null
                }
                val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, getStorage())
                HDWalletCryptoProvider(seedPhraseKey)
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

        return try {
            // --- New independent key storage (checked first) ---
            val switchUid = account.wallet?.id
            if (!switchUid.isNullOrBlank()) {
                val seedPhraseKey = KeyStorageManager.getSeedPhraseKey(switchUid)
                if (seedPhraseKey != null) {
                    logd("CryptoProviderManager", "Switch account new storage: seed phrase key for uid: $switchUid")
                    return HDWalletCryptoProvider(seedPhraseKey)
                }
                val privateKey = KeyStorageManager.getPrivateKeyObject(switchUid)
                if (privateKey != null) {
                    logd("CryptoProviderManager", "Switch account new storage: private key object for uid: $switchUid")
                    val keyWallet = WalletFactory.createKeyWallet(privateKey, setOf(ChainId.Mainnet, ChainId.Testnet), storage) as KeyWallet
                    val address = account.firstFlowWalletAddress() ?: ""
                    return runBlocking { createPrivateKeyCryptoProvider(privateKey, keyWallet, address) }
                }
            }

            // Handle keystore-based accounts
            if (account.keyStoreInfo.isNullOrBlank().not()) {
                PrivateKeyStoreCryptoProvider(account.keyStoreInfo!!)
            }

            // Handle mnemonic-only accounts (cleaner architecture - no prefix, just mnemonic)
            // Check this BEFORE prefix to properly handle new RN seed phrase accounts
            else if (account.prefix.isNullOrBlank() && !account.wallet?.id.isNullOrBlank() &&
                     AccountWalletManager.hasHDWalletKeystore(account.wallet?.id ?: "")) {
                logd("CryptoProviderManager", "Switch account: Mnemonic-only account (cleaner architecture)")
                val userId = account.wallet?.id ?: ""
                val mnemonic = AccountWalletManager.getHDWalletMnemonicByUID(userId)
                if (mnemonic == null) {
                    loge("CryptoProviderManager", "Switch account: Failed to get mnemonic by UID: $userId")
                    ErrorReporter.reportWithMixpanel(AccountError.GET_WALLET_FAILED)
                    return null
                }
                val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, getStorage())
                HDWalletCryptoProvider(seedPhraseKey)
            }

            // Handle prefix-based accounts (legacy or hardware-backed)
            // Also check new AKP storage when account.prefix is absent
            else if (!account.prefix.isNullOrBlank() || (!switchUid.isNullOrBlank() && KeyStorageManager.hasAndroidKeystorePrefix(switchUid))) {
                val switchEffectivePrefix = account.prefix?.takeIf { it.isNotBlank() }
                    ?: switchUid?.let { KeyStorageManager.getAndroidKeystorePrefix(it) }
                if (switchEffectivePrefix.isNullOrBlank()) {
                    loge("CryptoProviderManager", "Switch account: resolved prefix is blank")
                    return null
                }
                // Load the stored private key using the prefix-based ID with backward compatibility
                val privateKey = try {
                    KeyCompatibilityManager.getPrivateKeyWithFallback(switchEffectivePrefix, storage)
                } catch (e: HardwareBackedKeyException) {
                    loge("CryptoProviderManager", "Hardware-backed key detected for switch account")
                    loge("CryptoProviderManager", "Creating AndroidKeystoreCryptoProvider for hardware-backed key")
                    // Return AndroidKeystoreCryptoProvider directly
                    return AndroidKeystoreCryptoProvider(e.prefix!!)
                }

                if (privateKey == null) {
                    loge("CryptoProviderManager", "CRITICAL ERROR: Failed to load stored private key for switch account from both new and old storage")
                    loge("CryptoProviderManager", "Cannot proceed without the stored key as it would create a different account")
                    return null
                }

                // Create a proper KeyWallet directly with the PrivateKey
                val wallet = WalletFactory.createKeyWallet(
                    privateKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet

                // Determine the correct signing algorithm for this private key from on-chain data
                // Try BOTH P256 and secp256k1 public keys to match against on-chain keys
                val p256PublicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()
                val secp256k1PublicKey = privateKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()
                var determinedSigningAlgorithm = SigningAlgorithm.ECDSA_P256 // Default
                var determinedHashingAlgorithm: HashingAlgorithm? = null
                val flowWalletAddress = account.firstFlowWalletAddress() ?: ""

                if (flowWalletAddress.isNotEmpty()) {
                    try {
                        val onChainAccount = runBlocking { FlowCadenceApi.getAccount(flowWalletAddress) }
                        logd("CryptoProviderManager", "Fetched on-chain account for $flowWalletAddress with ${onChainAccount.keys?.size ?: 0} keys")

                        // Helper function to check if public keys match
                        fun publicKeysMatch(providerPubKey: String?, onChainPubKey: String): Boolean {
                            if (providerPubKey == null) return false
                            val acctPubKeyHex = onChainPubKey.removePrefix("0x").lowercase()
                            val providerPubKeyHex = providerPubKey.removePrefix("0x").lowercase()
                            val providerPubKeyStripped = if (providerPubKeyHex.startsWith("04") && providerPubKeyHex.length == 130) providerPubKeyHex.substring(2) else providerPubKeyHex
                            return acctPubKeyHex == providerPubKeyHex || acctPubKeyHex == providerPubKeyStripped
                        }

                        // Try to find matching on-chain key with P256 first
                        var matchedKey = onChainAccount.keys?.find { acctKey ->
                            val isMatch = publicKeysMatch(p256PublicKey, acctKey.publicKey) && !acctKey.revoked
                            if (isMatch) logd("CryptoProviderManager", "Matched on-chain key with P256: index=${acctKey.index}, signAlgo=${acctKey.signingAlgorithm}, hashAlgo=${acctKey.hashingAlgorithm}")
                            isMatch
                        }

                        // If P256 didn't match, try secp256k1
                        if (matchedKey == null) {
                            logd("CryptoProviderManager", "No P256 match found, trying secp256k1...")
                            matchedKey = onChainAccount.keys?.find { acctKey ->
                                val isMatch = publicKeysMatch(secp256k1PublicKey, acctKey.publicKey) && !acctKey.revoked
                                if (isMatch) logd("CryptoProviderManager", "Matched on-chain key with secp256k1: index=${acctKey.index}, signAlgo=${acctKey.signingAlgorithm}, hashAlgo=${acctKey.hashingAlgorithm}")
                                isMatch
                            }
                        }

                        if (matchedKey != null) {
                            determinedSigningAlgorithm = matchedKey.signingAlgorithm
                            determinedHashingAlgorithm = matchedKey.hashingAlgorithm
                            logd("CryptoProviderManager", "Successfully determined on-chain algorithms: signing=$determinedSigningAlgorithm, hashing=$determinedHashingAlgorithm")
                        } else {
                            logd("CryptoProviderManager", "Could NOT find matching on-chain key with either P256 or secp256k1. Using defaults.")
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
                val mnemonic = AccountWalletManager.getHDWalletMnemonicByUID(account.wallet?.id ?: "")
                if (mnemonic == null) {
                    loge("CryptoProviderManager", "Failed to get existing wallet for account ${account.userInfo.username}")
                    ErrorReporter.reportWithMixpanel(AccountError.GET_WALLET_FAILED)
                    return null
                }
                val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, getStorage())
                HDWalletCryptoProvider(seedPhraseKey)
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
            // --- New independent key storage (checked first for LocalSwitchAccount) ---
            val localUid = switchAccount.userId
            if (!localUid.isNullOrBlank()) {
                val seedPhraseKey = KeyStorageManager.getSeedPhraseKey(localUid)
                if (seedPhraseKey != null) {
                    logd("CryptoProviderManager", "LocalSwitchAccount new storage: seed phrase key for uid: $localUid")
                    return HDWalletCryptoProvider(seedPhraseKey)
                }
                val privateKey = KeyStorageManager.getPrivateKeyObject(localUid)
                if (privateKey != null) {
                    logd("CryptoProviderManager", "LocalSwitchAccount new storage: private key object for uid: $localUid")
                    val keyWallet = WalletFactory.createKeyWallet(privateKey, setOf(ChainId.Mainnet, ChainId.Testnet), storage) as KeyWallet
                    val address = switchAccount.address
                    return runBlocking { createPrivateKeyCryptoProvider(privateKey, keyWallet, address) }
                }
            }

            // Handle prefix-based accounts
            // Also check new AKP storage when switchAccount.prefix is absent
            val localEffectivePrefix = switchAccount.prefix?.takeIf { it.isNotBlank() }
                ?: localUid?.let { KeyStorageManager.getAndroidKeystorePrefix(it) }
            if (!localEffectivePrefix.isNullOrBlank()) {
                // Load the stored private key using the prefix-based ID with backward compatibility
                val privateKey = try {
                    KeyCompatibilityManager.getPrivateKeyWithFallback(localEffectivePrefix, storage)
                } catch (e: HardwareBackedKeyException) {
                    loge("CryptoProviderManager", "Hardware-backed key detected for local switch account prefix $localEffectivePrefix")
                    loge("CryptoProviderManager", "Creating AndroidKeystoreCryptoProvider for hardware-backed key")

                    // For LocalSwitchAccount, we use defaults since we don't have wallet address
                    return AndroidKeystoreCryptoProvider(e.prefix!!)
                }

                if (privateKey == null) {
                    loge("CryptoProviderManager", "CRITICAL ERROR: Failed to load stored private key for local switch account prefix $localEffectivePrefix from both new and old storage")
                    loge("CryptoProviderManager", "Cannot proceed without the stored key as it would create a different account")
                    return null
                }

                // Create a proper KeyWallet directly with the PrivateKey
                val wallet = WalletFactory.createKeyWallet(
                    privateKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                ) as KeyWallet

                // For prefix-based accounts, we use PrivateKeyCryptoProvider instead of BackupCryptoProvider
                val address = switchAccount.address
                runBlocking { createPrivateKeyCryptoProvider(privateKey, wallet, address) }
            }

            // Handle other accounts
            else {
                val mnemonic = AccountWalletManager.getHDWalletMnemonicByUID(switchAccount.userId ?: "")
                if (mnemonic == null) {
                    loge("CryptoProviderManager", "Failed to get existing wallet for switch account ${switchAccount.username}")
                    ErrorReporter.reportWithMixpanel(AccountError.GET_WALLET_FAILED)
                    return null
                }
                val seedPhraseKey = createSeedPhraseKeyWithKeyPair(mnemonic, getStorage())
                HDWalletCryptoProvider(seedPhraseKey)
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

    /**
     * Create a PrivateKeyCryptoProvider by resolving the correct signing and hashing algorithms
     * from the on-chain account keys. Falls back to ECDSA_P256 defaults if the lookup fails.
     */
    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun createPrivateKeyCryptoProvider(
        privateKey: PrivateKey,
        keyWallet: KeyWallet,
        address: String
    ): PrivateKeyCryptoProvider {
        if (address.isNotEmpty()) {
            try {
                val onChainAccount = FlowCadenceApi.getAccount(address)
                val onChainKeys = onChainAccount.keys?.filter { !it.revoked } ?: emptyList()

                for (sigAlgo in listOf(SigningAlgorithm.ECDSA_P256, SigningAlgorithm.ECDSA_secp256k1)) {
                    val pubKey = privateKey.publicKey(sigAlgo)?.toHexString() ?: continue
                    val matched = onChainKeys.find { isKeyMatchRobust(pubKey, it.publicKey) }
                    if (matched != null) {
                        logd(TAG, "createPrivateKeyCryptoProvider: matched on-chain key sigAlgo=$sigAlgo hashAlgo=${matched.hashingAlgorithm}")
                        return PrivateKeyCryptoProvider(privateKey, keyWallet, sigAlgo, matched.hashingAlgorithm)
                    }
                }
                logd(TAG, "createPrivateKeyCryptoProvider: no on-chain match for $address, using defaults")
            } catch (e: Exception) {
                logd(TAG, "createPrivateKeyCryptoProvider: on-chain lookup failed: ${e.message}, using defaults")
            }
        }
        return PrivateKeyCryptoProvider(privateKey, keyWallet, SigningAlgorithm.ECDSA_P256)
    }

    fun clear() {
        cryptoProvider = null
    }

    /**
     * Create a SeedPhraseKey with properly initialized storage
     */
    @OptIn(ExperimentalStdlibApi::class)
    private fun createSeedPhraseKeyWithKeyPair(mnemonic: String, storage: StorageProtocol): SeedPhraseKey {
        logd(TAG, "Creating SeedPhraseKey")

        try {
            // Create SeedPhraseKey
            val seedPhraseKey = SeedPhraseKey(
                mnemonicString = mnemonic,
                passphrase = "",
                derivationPath = DERIVATION_PATH,
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
            throw RuntimeException("Failed to create SeedPhraseKey", e)
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

    /**
     * Calculate similarity between two strings (0.0 to 1.0)
     * Helps identify potential format issues
     */
    private fun calculateSimilarity(str1: String, str2: String): Double {
        val maxLength = maxOf(str1.length, str2.length)
        if (maxLength == 0) return 1.0

        var matches = 0
        val minLength = minOf(str1.length, str2.length)
        for (i in 0 until minLength) {
            if (str1[i] == str2[i]) matches++
        }

        return matches.toDouble() / maxLength
    }
}

