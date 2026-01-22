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
import com.flowfoundation.wallet.wallet.Wallet
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
                logd(TAG, "  Branch: Prefix-based account")

                // Standard prefix-based account handling (for non-multi-restore accounts)
                logd(TAG, "  Standard prefix-based account handling")

                // Try to get the private key, handling hardware-backed keys
                val privateKey = try {
                    KeyCompatibilityManager.getPrivateKeyWithFallback(account.prefix!!, storage)
                } catch (e: HardwareBackedKeyException) {
                    loge(TAG, "Hardware-backed key detected")

                    // Determine the correct algorithms by checking on-chain keys
                    var determinedSigningAlgorithm = SigningAlgorithm.ECDSA_P256
                    var determinedHashingAlgorithm: HashingAlgorithm? = null

                    try {
                        val accountAddress = account.wallet?.walletAddress()
                        if (accountAddress != null) {
                            val onChainAccount = runBlocking { FlowCadenceApi.getAccount(accountAddress) }
                            val onChainKeys = onChainAccount.keys?.toList() ?: emptyList()

                            // Create temporary AndroidKeystoreCryptoProvider to get public key for matching
                            val tempProvider = AndroidKeystoreCryptoProvider(e.alias!!, SigningAlgorithm.ECDSA_P256)
                            val keystorePublicKey = tempProvider.getPublicKey()

                            // Find matching on-chain key to determine algorithms
                            val matchedKey = onChainKeys.find { onChainKey ->
                                isKeyMatchRobust("0x$keystorePublicKey", onChainKey.publicKey) && !onChainKey.revoked
                            }

                            if (matchedKey != null) {
                                determinedSigningAlgorithm = matchedKey.signingAlgorithm
                                determinedHashingAlgorithm = matchedKey.hashingAlgorithm
                                logd(TAG, "  Hardware-backed key matched on-chain: signing=$determinedSigningAlgorithm, hashing=$determinedHashingAlgorithm")
                            } else {
                                // Try secp256k1 if P256 didn't match
                                val tempProviderSecp = AndroidKeystoreCryptoProvider(e.alias, SigningAlgorithm.ECDSA_secp256k1)
                                val keystorePublicKeySecp = tempProviderSecp.getPublicKey()

                                val matchedKeySecp = onChainKeys.find { onChainKey ->
                                    isKeyMatchRobust("0x$keystorePublicKeySecp", onChainKey.publicKey) && !onChainKey.revoked
                                }

                                if (matchedKeySecp != null) {
                                    determinedSigningAlgorithm = matchedKeySecp.signingAlgorithm
                                    determinedHashingAlgorithm = matchedKeySecp.hashingAlgorithm
                                    logd(TAG, "  Hardware-backed key matched on-chain with secp256k1: signing=$determinedSigningAlgorithm, hashing=$determinedHashingAlgorithm")
                                } else {
                                    logd(TAG, "  Hardware-backed key: Could not find matching on-chain key, using defaults")
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        loge(TAG, "  Hardware-backed key: Error determining algorithms: ${ex.message}, using defaults")
                    }

                    // Return AndroidKeystoreCryptoProvider directly
                    return AndroidKeystoreCryptoProvider(e.alias!!, determinedSigningAlgorithm, determinedHashingAlgorithm)
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
                    val accountAddress = account.wallet?.walletAddress()
                    if (accountAddress != null) {
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
            // Handle wallet-specific mnemonic accounts
            else {
                logd(TAG, "  Branch: Inactive account.")
                val mnemonic = if (account.isActive) {
                    Wallet.store().wallet().mnemonic()
                } else {
                    AccountWalletManager.getHDWalletMnemonicByUID(account.wallet?.id ?: "")
                }

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
            // Handle keystore-based accounts
            if (account.keyStoreInfo.isNullOrBlank().not()) {
                PrivateKeyStoreCryptoProvider(account.keyStoreInfo!!)
            }

            // Handle prefix-based accounts
            else if (account.prefix.isNullOrBlank().not()) {
                // Load the stored private key using the prefix-based ID with backward compatibility
                val privateKey = try {
                    KeyCompatibilityManager.getPrivateKeyWithFallback(account.prefix!!, storage)
                } catch (e: HardwareBackedKeyException) {
                    loge("CryptoProviderManager", "Hardware-backed key detected for switch account")
                    loge("CryptoProviderManager", "Creating AndroidKeystoreCryptoProvider for hardware-backed key")

                    // Determine the correct algorithms by checking on-chain keys
                    var determinedSigningAlgorithm = SigningAlgorithm.ECDSA_P256
                    var determinedHashingAlgorithm: HashingAlgorithm? = null

                    try {
                        val accountAddress = account.wallet?.walletAddress()
                        if (accountAddress != null) {
                            val onChainAccount = runBlocking { FlowCadenceApi.getAccount(accountAddress) }
                            val onChainKeys = onChainAccount.keys?.toList() ?: emptyList()

                            // Create temporary AndroidKeystoreCryptoProvider to get public key for matching
                            val tempProvider = AndroidKeystoreCryptoProvider(e.alias!!, SigningAlgorithm.ECDSA_P256)
                            val keystorePublicKey = tempProvider.getPublicKey()

                            // Find matching on-chain key to determine algorithms
                            val matchedKey = onChainKeys.find { onChainKey ->
                                isKeyMatchRobust("0x$keystorePublicKey", onChainKey.publicKey) && !onChainKey.revoked
                            }

                            if (matchedKey != null) {
                                determinedSigningAlgorithm = matchedKey.signingAlgorithm
                                determinedHashingAlgorithm = matchedKey.hashingAlgorithm
                                logd("CryptoProviderManager", "Switch account hardware-backed key matched on-chain: signing=$determinedSigningAlgorithm, hashing=$determinedHashingAlgorithm")
                            } else {
                                // Try secp256k1 if P256 didn't match
                                val tempProviderSecp = AndroidKeystoreCryptoProvider(e.alias, SigningAlgorithm.ECDSA_secp256k1)
                                val keystorePublicKeySecp = tempProviderSecp.getPublicKey()

                                val matchedKeySecp = onChainKeys.find { onChainKey ->
                                    isKeyMatchRobust("0x$keystorePublicKeySecp", onChainKey.publicKey) && !onChainKey.revoked
                                }

                                if (matchedKeySecp != null) {
                                    determinedSigningAlgorithm = matchedKeySecp.signingAlgorithm
                                    determinedHashingAlgorithm = matchedKeySecp.hashingAlgorithm
                                    logd("CryptoProviderManager", "Switch account hardware-backed key matched on-chain with secp256k1: signing=$determinedSigningAlgorithm, hashing=$determinedHashingAlgorithm")
                                } else {
                                    logd("CryptoProviderManager", "Switch account hardware-backed key: Could not find matching on-chain key, using defaults")
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        loge("CryptoProviderManager", "Switch account hardware-backed key: Error determining algorithms: ${ex.message}, using defaults")
                    }

                    // Return AndroidKeystoreCryptoProvider directly
                    return AndroidKeystoreCryptoProvider(e.alias!!, determinedSigningAlgorithm, determinedHashingAlgorithm)
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
            // Handle prefix-based accounts
            if (switchAccount.prefix.isNullOrBlank().not()) {
                // Load the stored private key using the prefix-based ID with backward compatibility
                val privateKey = try {
                    KeyCompatibilityManager.getPrivateKeyWithFallback(switchAccount.prefix, storage)
                } catch (e: HardwareBackedKeyException) {
                    loge("CryptoProviderManager", "Hardware-backed key detected for local switch account prefix ${switchAccount.prefix}")
                    loge("CryptoProviderManager", "Creating AndroidKeystoreCryptoProvider for hardware-backed key")

                    // For LocalSwitchAccount, we use defaults since we don't have wallet address
                    return AndroidKeystoreCryptoProvider(e.alias!!, SigningAlgorithm.ECDSA_P256, null)
                }

                if (privateKey == null) {
                    loge("CryptoProviderManager", "CRITICAL ERROR: Failed to load stored private key for local switch account prefix ${switchAccount.prefix} from both new and old storage")
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
                PrivateKeyCryptoProvider(privateKey, wallet)
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
