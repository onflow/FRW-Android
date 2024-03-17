package com.flowfoundation.wallet.page.profile.subpage.walletconnect.session

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.manager.walletconnect.getWalletConnectPendingRequests
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.model.PendingRequestModel
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.model.WalletConnectSessionTitleModel
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.viewModelIOScope

class WalletConnectSessionViewModel : ViewModel() {

    val dataListLiveData = MutableLiveData<List<Any>>()

    fun load() {
        viewModelIOScope(this) {
            val data = mutableListOf<Any>().apply {
                val sessions = WalletConnect.get().sessions()
                val requests = getWalletConnectPendingRequests().map { request ->
                    PendingRequestModel(
                        request = request,
                        metadata = sessions.firstOrNull { request.topic == it.topic }?.metaData
                    )
                }.filter { it.metadata != null }
                if (requests.isNotEmpty()) {
                    add(WalletConnectSessionTitleModel(R.string.pending_request.res2String()))
                    addAll(requests)
                }

                add(WalletConnectSessionTitleModel(R.string.connected_site.res2String()))
                addAll(sessions)
            }
            if (data.size > 1) {
                dataListLiveData.postValue(data)
            } else {
                dataListLiveData.postValue(emptyList())
            }
        }
    }
}