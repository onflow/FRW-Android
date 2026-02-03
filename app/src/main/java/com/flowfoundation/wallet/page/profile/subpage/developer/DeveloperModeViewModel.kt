package com.flowfoundation.wallet.page.profile.subpage.developer

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.isRegistered
import com.flowfoundation.wallet.utils.viewModelIOScope

class DeveloperModeViewModel : ViewModel() {
    val progressVisibleLiveData = MutableLiveData<Boolean>()

    val resultLiveData = MutableLiveData<Boolean>()

    fun changeNetwork() {
        viewModelIOScope(this) {
            FlowCadenceApi.refreshConfig()
            val cacheExist = WalletManager.wallet() != null && !WalletManager.getCurrentFlowWalletAddress().isNullOrBlank()
            if (!cacheExist && isRegistered()) {
                progressVisibleLiveData.postValue(true)
                resultLiveData.postValue(true)
                progressVisibleLiveData.postValue(false)
            }
        }
    }
}
