package com.flowfoundation.wallet.page.restore.mnemonic

import androidx.lifecycle.ViewModel
import com.flow.wallet.keys.SeedPhraseKey
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.firstFlowWalletAddress
import com.flowfoundation.wallet.manager.key.storage.KeyStorageManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletdata.WalletDataManager
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import com.flowfoundation.wallet.utils.Env.getStorage
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.secret.EncryptedMnemonicUtils
import com.flowfoundation.wallet.wallet.DERIVATION_PATH
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.onflow.flow.models.SigningAlgorithm
import wallet.core.jni.HDWallet

class RestoreMnemonicViewModel : ViewModel() {

    private val TAG = "RestoreMnemonicViewModel"

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _restoreSuccess = MutableStateFlow(false)
    val restoreSuccess: StateFlow<Boolean> = _restoreSuccess.asStateFlow()

    private val _mnemonicMismatch = MutableStateFlow(false)
    val mnemonicMismatch: StateFlow<Boolean> = _mnemonicMismatch.asStateFlow()

    fun resetMnemonicMismatch() {
        _mnemonicMismatch.value = false
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun restoreMnemonic(mnemonic: String) {
        if (!validateMnemonic(mnemonic)) {
            // Should handle validation error in UI
            return
        }

        _isRestoring.value = true
        ioScope {
            val currentAccount = AccountManager.get() ?: return@ioScope
            val uid = firebaseUid()
            if (uid.isNullOrBlank()) {
                _isRestoring.value = false
                return@ioScope
            }

            val currentKeyStoreInfo = currentAccount.keyStoreInfo

            // Validate that the mnemonic derives the same public key as stored in keyStoreInfo
            if (!currentKeyStoreInfo.isNullOrBlank()) {
                try {
                    val ks = Gson().fromJson(currentKeyStoreInfo, KeystoreAddress::class.java)
                    val seedPhraseKey = SeedPhraseKey(
                        mnemonicString = mnemonic,
                        passphrase = "",
                        derivationPath = DERIVATION_PATH,
                        storage = getStorage()
                    )
                    val derivedPubKey = when (ks.signAlgo) {
                        SigningAlgorithm.ECDSA_secp256k1.cadenceIndex ->
                            seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_secp256k1)?.toHexString()?.removePrefix("04")
                        else -> // ECDSA_P256
                            seedPhraseKey.publicKey(SigningAlgorithm.ECDSA_P256)?.toHexString()?.removePrefix("04")
                    }
                    val storedPubKey = ks.publicKey.removePrefix("0x").lowercase()
                    if (derivedPubKey == null || !derivedPubKey.equals(storedPubKey, ignoreCase = true)) {
                        _mnemonicMismatch.value = true
                        _isRestoring.value = false
                        return@ioScope
                    }
                } catch (e: Exception) {
                    loge(TAG, "Failed to validate mnemonic public key: ${e.message}")
                    _isRestoring.value = false
                    return@ioScope
                }
            }

            // Encrypt mnemonic
            val encryptedMnemonic = EncryptedMnemonicUtils.encrypt(mnemonic, uid)

            // Update Account keystore info
            if (!currentKeyStoreInfo.isNullOrBlank()) {
                try {
                    // Use atomic update to ensure keystoreInfo is updated safely
                    AccountManager.updateCurrentAccount { account ->
                        val currentKSInfo = account.keyStoreInfo
                        if (!currentKSInfo.isNullOrBlank()) {
                            val keystoreAddress = Gson().fromJson(currentKSInfo, KeystoreAddress::class.java)
                            val newKeystoreAddress = keystoreAddress.copy(encryptedMnemonic = encryptedMnemonic)
                            account.copy(keyStoreInfo = Gson().toJson(newKeystoreAddress))
                        } else {
                            // This case should ideally not happen if keyStoreInfo was set initially.
                            // But if it does, we return the original account or handle it as an error.
                            account
                        }
                    }
                    // Clear wallet cache and re-initialize
                    WalletManager.clear()
                    WalletDataManager.updateCurrentAccount()

                    // Sync mnemonic to independent key storage and remove redundant private key entry
                    try {
                        KeyStorageManager.saveSeedPhrase(uid, mnemonic)
                        KeyStorageManager.deletePrivateKey(uid)
                        val address = AccountManager.get()?.firstFlowWalletAddress()
                        if (!address.isNullOrBlank()) {
                            KeyStorageManager.saveWalletAddress(uid, address)
                        }
                    } catch (e: Exception) {
                        loge(TAG, "KeyStorageManager sync failed: ${e.message}")
                    }

                    _restoreSuccess.value = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _isRestoring.value = false
        }
    }

    fun validateMnemonic(mnemonic: String): Boolean {
        return try {
            HDWallet(mnemonic, "")
            true
        } catch (e: Exception) {
            false
        }
    }
}
