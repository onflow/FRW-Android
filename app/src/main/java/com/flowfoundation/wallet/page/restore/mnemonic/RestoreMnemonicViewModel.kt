package com.flowfoundation.wallet.page.restore.mnemonic

import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletdata.WalletDataManager
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.secret.EncryptedMnemonicUtils
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import wallet.core.jni.HDWallet

class RestoreMnemonicViewModel : ViewModel() {

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _restoreSuccess = MutableStateFlow(false)
    val restoreSuccess: StateFlow<Boolean> = _restoreSuccess.asStateFlow()

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

            // Encrypt mnemonic
            val encryptedMnemonic = EncryptedMnemonicUtils.encrypt(mnemonic, uid)

            // Update Account keystore info
            val currentKeyStoreInfo = currentAccount.keyStoreInfo
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
