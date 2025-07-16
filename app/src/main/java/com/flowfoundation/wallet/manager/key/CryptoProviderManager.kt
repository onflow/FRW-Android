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
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.BuildConfig

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

}

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

        logd(TAG, "generateAccountCryptoProvider: Generating for account: ${account.userInfo.username}, isActive: ${account.isActive}, prefix: ${account.prefix}, hasKeystore: ${!account.keyStoreInfo.isNullOrBlank()}")

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
                    
                    // Only use multi-signature if we don't have a working device key with sufficient weight
                    if (multiRestoreCount > 0 && multiRestoreAddress == account.wallet?.walletAddress()) {
                        logd(TAG, "  Multi-restore account detected, checking device key availability...")
                        
                        // Check timing to help diagnose key indexer latency
                        val restoreCompletedTime = passwordMap["multi_restore_completed_time"]?.toLongOrNull()
                        if (restoreCompletedTime != null) {
                            val timeSinceRestore = (System.currentTimeMillis() - restoreCompletedTime) / 1000
                            logd(TAG, "  Time since restore completed: ${timeSinceRestore}s ago")
                            if (timeSinceRestore < 60) {
                                logd(TAG, "  ⏱️ Recent restore detected (<60s) - key indexer may still be processing")
                            }
                        } else {
                            logd(TAG, "  No restore completion time recorded")
                        }
                        
                        // Check if we have a device key that works and has sufficient weight
                        val keyId = "prefix_key_${account.prefix}"
                        val hasDeviceKey = try {
                            val deviceKey = PrivateKey.get(keyId, account.prefix!!, storage)
                            deviceKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()
                                ?: deviceKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()
                            
                            logd(TAG, "    Device key check: keyId=$keyId, prefix=${account.prefix}")
                            
                            // Test both signing algorithms for the device key
                            val deviceKeyECDSA_P256 = try {
                                val key = deviceKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()
                                logd(TAG, "    Device key ECDSA_P256 public key: $key")
                                key
                            } catch (e: Exception) {
                                logd(TAG, "    Device key ECDSA_P256 failed: ${e.message}")
                                null
                            }
                            
                            val deviceKeyECDSA_secp256k1 = try {
                                val key = deviceKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()
                                logd(TAG, "    Device key ECDSA_secp256k1 public key: $key")
                                key
                            } catch (e: Exception) {
                                logd(TAG, "    Device key ECDSA_secp256k1 failed: ${e.message}")
                                null
                            }
                            
                            logd(TAG, "    Fetching on-chain account for address: ${account.wallet?.walletAddress()}")
                            
                            val onChainAccount = runBlocking { 
                                FlowCadenceApi.getAccount(account.wallet?.walletAddress() ?: "")
                            }
                            val onChainKeys = onChainAccount.keys?.toList() ?: emptyList()
                            
                            logd(TAG, "    On-chain account has ${onChainKeys.size} total keys:")
                            
                            // Show recent keys (last 10) with more detail
                            val recentKeys = onChainKeys.takeLast(10)
                            recentKeys.forEachIndexed { index, key ->
                                val actualIndex = onChainKeys.size - recentKeys.size + index
                                logd(TAG, "      [$actualIndex] Key $actualIndex: weight=${key.weight}, revoked=${key.revoked}, pubKey=${key.publicKey.take(20)}...")
                            }
                            
                            // Test device key matching with detailed format analysis
                            var deviceKeyFound = false
                            var bestMatch = ""
                            var bestMatchInfo = ""

                            listOf(deviceKeyECDSA_P256, deviceKeyECDSA_secp256k1).filterNotNull().forEach { devicePublicKey ->
                                val signingAlgo = if (devicePublicKey == deviceKeyECDSA_P256) "ECDSA_P256" else "ECDSA_secp256k1"
                                logd(TAG, "    Testing device key ($signingAlgo) against on-chain keys...")
                                logd(TAG, "      Device key full: $devicePublicKey")
                                logd(TAG, "      Device key length: ${devicePublicKey.length}")
                                
                                // Show different format variations
                                val deviceRaw = devicePublicKey.removePrefix("0x").lowercase()
                                val deviceStripped = if (deviceRaw.startsWith("04") && deviceRaw.length == 130) deviceRaw.substring(2) else deviceRaw
                                val deviceWith04 = if (!deviceRaw.startsWith("04") && deviceRaw.length == 128) "04$deviceRaw" else deviceRaw
                                
                                logd(TAG, "      Device key variations:")
                                logd(TAG, "        Raw: $deviceRaw")
                                logd(TAG, "        Stripped: $deviceStripped")
                                logd(TAG, "        With04: $deviceWith04")
                                
                                onChainKeys.forEachIndexed { index, onChainKey ->
                                    if (!onChainKey.revoked && onChainKey.weight.toIntOrNull()?.let { it >= 1000 } == true) {
                                        val onChainRaw = onChainKey.publicKey.removePrefix("0x").lowercase()
                                        val onChainStripped = if (onChainRaw.startsWith("04") && onChainRaw.length == 130) onChainRaw.substring(2) else onChainRaw
                                        val onChainWith04 = if (!onChainRaw.startsWith("04") && onChainRaw.length == 128) "04$onChainRaw" else onChainRaw
                                        
                                        val isMatch = isKeyMatchRobust(devicePublicKey, onChainKey.publicKey)
                                        
                                        if (isMatch) {
                                            logd(TAG, "        ✅ MATCH FOUND! Key $index ($signingAlgo)")
                                            logd(TAG, "          On-chain: ${onChainKey.publicKey}")
                                            logd(TAG, "          Device:   $devicePublicKey")
                                            logd(TAG, "          Weight: ${onChainKey.weight}")
                                            deviceKeyFound = true
                                            bestMatch = onChainKey.publicKey
                                            bestMatchInfo = "Key $index ($signingAlgo, weight=${onChainKey.weight})"
                                        } else {
                                            // Show detailed comparison for high-weight keys
                                            logd(TAG, "        ❌ No match Key $index ($signingAlgo, weight=${onChainKey.weight}):")
                                            logd(TAG, "          OnChain: ${onChainKey.publicKey.take(40)}...")
                                            logd(TAG, "          Device:  ${devicePublicKey.take(40)}...")
                                            
                                            // Show format comparison
                                            logd(TAG, "          Format comparison:")
                                            logd(TAG, "            OnChain raw vs Device raw: ${onChainRaw == deviceRaw}")
                                            logd(TAG, "            OnChain stripped vs Device stripped: ${onChainStripped == deviceStripped}")
                                            logd(TAG, "            OnChain with04 vs Device with04: ${onChainWith04 == deviceWith04}")
                                            
                                            // Check similarity
                                            val similarity = calculateSimilarity(onChainRaw, deviceRaw)
                                            if (similarity > 0.8) {
                                                logd(TAG, "            ⚠️ High similarity: ${(similarity * 100).toInt()}% - possible format issue")
                                            }
                                        }
                                    }
                                }
                            }
                            
                            if (deviceKeyFound) {
                                logd(TAG, "    ✅ DEVICE KEY FOUND ON-CHAIN: $bestMatchInfo")
                                logd(TAG, "    Matched key: $bestMatch")
                                true
                            } else {
                                logd(TAG, "    ❌ DEVICE KEY NOT FOUND ON-CHAIN")
                                logd(TAG, "      This suggests either:")
                                logd(TAG, "      1. Key indexer latency - device key may not be indexed yet")
                                logd(TAG, "      2. Algorithm mismatch - key created with different algorithm")
                                logd(TAG, "      3. Key format issue - encoding differences")
                                logd(TAG, "      4. Different key stored locally vs on-chain")
                                
                                // Show what we're looking for vs what's available
                                if (deviceKeyECDSA_P256 != null) {
                                    logd(TAG, "      Looking for ECDSA_P256: ${deviceKeyECDSA_P256.take(40)}...")
                                }
                                if (deviceKeyECDSA_secp256k1 != null) {
                                    logd(TAG, "      Looking for ECDSA_secp256k1: ${deviceKeyECDSA_secp256k1.take(40)}...")
                                }
                                
                                logd(TAG, "      Available high-weight keys on-chain:")
                                onChainKeys.forEachIndexed { index, key ->
                                    if (!key.revoked && key.weight.toIntOrNull()?.let { it >= 1000 } == true) {
                                        logd(TAG, "        Key $index: ${key.publicKey.take(40)}... (weight=${key.weight})")
                                    }
                                }
                                false
                            }
                        } catch (e: Exception) {
                            logd(TAG, "  Device key check failed: ${e.message}")
                            logd(TAG, "  This could indicate:")
                            logd(TAG, "    1. Device key not stored yet")
                            logd(TAG, "    2. Key indexer latency") 
                            logd(TAG, "    3. Storage/network error")
                            false
                        }
                        
                        if (hasDeviceKey) {
                            logd(TAG, "  ✅ DECISION: Device key available with sufficient weight - using device key instead of multi-signature")
                            logd(TAG, "  This means subsequent transactions will use fast single-signature with the 1000-weight device key")
                            false // Don't use multi-restore, use device key
                        } else {
                            logd(TAG, "  ⚠️ DECISION: Device key not available or insufficient weight - using multi-signature")
                            logd(TAG, "  This means subsequent transactions will use slower multi-signature with backup keys")
                            logd(TAG, "  If this happens immediately after restore, it's likely due to key indexer latency")
                            true // Use multi-restore
                        }
                    } else {
                        false
                    }
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