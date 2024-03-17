package com.flowfoundation.wallet.page.walletcreate.fragments.warning

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class WalletCreateWarningViewModel : ViewModel() {

    val registerCallbackLiveData = MutableLiveData<Boolean>()

    fun register() {
//        viewModelIOScope(this) {
//            try {
//                registerOutblockUser(username().lowercase(Locale.getDefault())) { isSuccess ->
//                    uiScope { registerCallbackLiveData.postValue(isSuccess) }
//                    if (isSuccess) {
//                        uploadPushToken()
//                        createWalletFromServer()
//                        Wallet.store().store()
//                    }
//                }
//            } catch (e: Exception) {
//                uiScope { registerCallbackLiveData.postValue(false) }
//            }
//        }
    }
}