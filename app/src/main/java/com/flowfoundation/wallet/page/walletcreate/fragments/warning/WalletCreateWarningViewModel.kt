package com.flowfoundation.wallet.page.walletcreate.fragments.warning

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class WalletCreateWarningViewModel : ViewModel() {

    val registerCallbackLiveData = MutableLiveData<Boolean>()

    fun register() {
//        viewModelIOScope(this) {
//            try {
//                registerOutblockUser(username().lowercase(Locale.getDefault())) { isSuccess ->
//                    withContext(Dispatchers.Main) {
//                        registerCallbackLiveData.postValue(isSuccess)
//                        if (isSuccess) {
//                            uploadPushToken()
//                            createWalletFromServer()
//                            // Initialize WalletManager with the new wallet
//                            WalletManager.init()
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                withContext(Dispatchers.Main) {
//                    registerCallbackLiveData.postValue(false)
//                }
//            }
//        }
    }

}