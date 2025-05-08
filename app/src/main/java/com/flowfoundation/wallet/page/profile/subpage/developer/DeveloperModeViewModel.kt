package com.flowfoundation.wallet.page.profile.subpage.developer

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.utils.isRegistered
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.viewModelIOScope

class DeveloperModeViewModel : ViewModel() {
    val progressVisibleLiveData = MutableLiveData<Boolean>()

    val resultLiveData = MutableLiveData<Boolean>()

    fun changeNetwork() {
        viewModelIOScope(this) {
            FlowCadenceApi.refreshConfig()
            val cacheExist = WalletManager.wallet() != null && !WalletManager.wallet()?.walletAddress().isNullOrBlank()
            if (!cacheExist && isRegistered()) {
                progressVisibleLiveData.postValue(true)
                try {
                    val service = retrofit().create(ApiService::class.java)
                    val resp = service.getWalletList()

                    // request success & wallet list is empty (wallet not create finish)
                    if (!resp.data!!.wallets.isNullOrEmpty()) {
                        AccountManager.updateWalletInfo(resp.data)
                        resultLiveData.postValue(true)
                    }
                } catch (e: Exception) {
                    loge(e)
                    resultLiveData.postValue(false)
                }
                progressVisibleLiveData.postValue(false)
            }
        }
    }
}