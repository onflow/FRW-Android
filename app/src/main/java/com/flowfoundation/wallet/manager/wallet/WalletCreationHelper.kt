package com.flowfoundation.wallet.manager.wallet

import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flow.wallet.keys.KeyFormat
import com.flow.wallet.keys.PrivateKey
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.StorageProtocol
import com.flow.wallet.wallet.Wallet
import com.flow.wallet.wallet.WalletFactory
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountWalletManager
import com.flowfoundation.wallet.manager.account.HardwareBackedKeyException
import com.flowfoundation.wallet.manager.key.AndroidKeystoreCryptoProvider
import com.flowfoundation.wallet.manager.key.KeyCompatibilityManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.secret.EncryptedMnemonicUtils
import com.flowfoundation.wallet.wallet.DERIVATION_PATH
import com.google.gson.Gson
import org.onflow.flow.ChainId
import org.onflow.flow.models.SigningAlgorithm
import org.onflow.flow.models.hexToBytes

/**
 * Helper class for creating Wallet objects from Account information
 * Provides reusable wallet creation logic for WalletManager and WalletDataManager
 */
object WalletCreationHelper {
    private val TAG = "WalletCreationHelper"

    /**
     * Create Wallet object from Account using only key-related information
     * This method focuses solely on cryptographic key data and ignores wallet/address info
     */
    suspend fun createWalletFromAccount(account: Account, isCurrentAccount: Boolean = true):
      Wallet? {
        return try {
            logd(TAG, "Creating wallet from account: ${account.userInfo.username}")

            // Create wallet based on account's key information only
            val wallet = when {
                // Handle keystore-based accounts
                !account.keyStoreInfo.isNullOrBlank() -> {
                    logd(TAG, "Creating keystore-based wallet for account: ${account.userInfo.username}")
                    createWalletFromKeystore(account.keyStoreInfo!!, userId = account.wallet?.id, isCurrentAccount)
                }

                // Handle prefix-based accounts
                !account.prefix.isNullOrBlank() -> {
                    logd(TAG, "Creating prefix-based wallet for account: ${account.userInfo.username}")
                    createWalletFromPrefix(account.prefix!!, isCurrentAccount)
                }

                // Handle HD wallet (fallback case)
                else -> {
                    logd(TAG, "Creating HD wallet for account: ${account.userInfo.username}")
                    createWalletFromHDMnemonic(account.wallet?.id ?: "", isCurrentAccount)
                }
            }

            if (wallet != null) {
                logd(TAG, "Successfully created wallet for account: ${account.userInfo.username}")
            } else {
                logd(TAG, "Failed to create wallet for account: ${account.userInfo.username}")
            }

            wallet
        } catch (e: Exception) {
            logd(TAG, "Error creating wallet for account ${account.userInfo.username}: ${e.message}")
            null
        }
    }

    /**
     * Create wallet from keystore information
     */
    private suspend fun createWalletFromKeystore(keyStoreInfo: String, userId: String?, isCurrentAccount: Boolean): Wallet {
        val ks = Gson().fromJson(keyStoreInfo, KeystoreAddress::class.java)
        val storage = getStorage()
        // Check if we have an encrypted mnemonic (HD Wallet restore)
        if (!ks.encryptedMnemonic.isNullOrBlank()) {
            logd(TAG, "Found encrypted mnemonic, creating HD Wallet")
            val uid = if (isCurrentAccount) firebaseUid() else userId
            if (!uid.isNullOrBlank()) {
                val decryptedMnemonic = EncryptedMnemonicUtils.decrypt(ks.encryptedMnemonic, uid)
                logd(TAG, "Decrypted mnemonic: $decryptedMnemonic")
                if (!decryptedMnemonic.isNullOrBlank()) {
                    // Create HD Wallet using the decrypted mnemonic
                    val seedPhraseKey = SeedPhraseKey(
                        mnemonicString = decryptedMnemonic,
                        passphrase = "",
                        derivationPath = DERIVATION_PATH,
                        storage = storage
                    )
                    if (isCurrentAccount) {
                        WalletManager.setEoaDisabled(false)
                    }
                    return WalletFactory.createKeyWallet(
                        seedPhraseKey,
                        setOf(ChainId.Mainnet, ChainId.Testnet),
                        storage
                    )
                } else {
                    logd(TAG, "Failed to decrypt mnemonic, using private key mode")
                }
            } else {
                logd(TAG, "No Firebase UID available for decryption, using private key mode")
            }
        } else {
            logd(TAG, "No encrypted mnemonic found, using private key mode")
            try {
                val service = retrofitApi().create(ApiService::class.java)
                val response = service.checkUserMnemonicStatus()
                logd(TAG, "Checked user mnemonic status: ${response.data}")
                if (response.data?.isExist == true) {
                    logd(TAG, "Mnemonic restore required. Disabling EOA and notifying UI.")
                    if (isCurrentAccount) {
                        WalletManager.setEoaDisabled(true)
                        LocalBroadcastManager.getInstance(com.flowfoundation.wallet.utils.Env.getApp())
                            .sendBroadcast(android.content.Intent("ACTION_RESTORE_MNEMONIC"))
                    }
                }
            } catch (e: Exception) {
                logd(TAG, "Error checking mnemonic status: ${e.message}")
            }
        }

        // Fallback to private key mode
        return createWalletFromKeystorePrivateKey(ks, storage, isCurrentAccount)
    }

    /**
     * Create wallet from prefix-based key
     * 
     * For Secure Enclave (hardware-backed) keys, EOA is disabled since we can't derive
     * an EOA address from a hardware key.
     */
    private fun createWalletFromPrefix(prefix: String, isCurrentAccount: Boolean): Wallet? {
        val storage = getStorage()
        return try {
            val privateKey = KeyCompatibilityManager.getPrivateKeyWithFallback(prefix, storage)
            if (privateKey != null) {
                logd(TAG, "Regular prefix-based key found for prefix: $prefix")
                WalletFactory.createKeyWallet(
                    privateKey,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                )
            } else {
                logd(TAG, "Private key not found for prefix: $prefix")
                null
            }
        } catch (e: HardwareBackedKeyException) {
            // Secure Enclave (hardware-backed) key - cannot derive EOA
            logd(TAG, "Hardware-backed key detected for prefix: $prefix - EOA disabled")
            if (isCurrentAccount) {
                WalletManager.setEoaDisabled(true)
            }
            if (e.alias != null) {
                val provider = AndroidKeystoreCryptoProvider(e.alias, SigningAlgorithm.ECDSA_P256, null)
                WalletFactory.createProxyWallet(
                    provider,
                    setOf(ChainId.Mainnet, ChainId.Testnet),
                    storage
                )
            } else {
                logd(TAG, "Hardware-backed key alias is null")
                null
            }
        }
    }

    /**
     * Create wallet from HD wallet mnemonic
     * HD wallets can derive EOA addresses.
     */
    private fun createWalletFromHDMnemonic(accountId: String, isCurrentAccount: Boolean): Wallet? {
        val storage = getStorage()
        val mnemonic = AccountWalletManager.getHDWalletMnemonicByUID(accountId)
        if (mnemonic != null) {
            val seedPhraseKey = SeedPhraseKey(
                mnemonicString = mnemonic,
                passphrase = "",
                derivationPath = DERIVATION_PATH,
                storage = storage
            )
            // HD wallet (mnemonic-based) - can derive EOA
            if (isCurrentAccount) {
                WalletManager.setEoaDisabled(false)
            }
            logd(TAG, "HD wallet from mnemonic - EOA enabled")
            return WalletFactory.createKeyWallet(
                seedPhraseKey,
                setOf(ChainId.Mainnet, ChainId.Testnet),
                storage
            )
        } else {
            logd(TAG, "HD wallet key not found for account ID: $accountId")
            return null
        }
    }

    /**
     * Create wallet from keystore private key
     * Private key imports cannot derive EOA addresses (no mnemonic available).
     */
    private fun createWalletFromKeystorePrivateKey(ks: KeystoreAddress, storage: StorageProtocol, isCurrentAccount: Boolean): Wallet {
        val keyHex = ks.privateKey.removePrefix("0x")
        require(keyHex.length == 64) { "Private key must be 32-byte hex" }

        val key = PrivateKey.create(storage).apply {
            importPrivateKey(keyHex.hexToBytes(), KeyFormat.RAW)
        }

        // Keystore private key import - cannot derive EOA (no mnemonic available)
        if (isCurrentAccount) {
            WalletManager.setEoaDisabled(true)
        }
        logd(TAG, "Keystore private key import - EOA disabled")

        return WalletFactory.createKeyWallet(
            key,
            setOf(ChainId.Mainnet, ChainId.Testnet),
            storage
        )
    }
}
