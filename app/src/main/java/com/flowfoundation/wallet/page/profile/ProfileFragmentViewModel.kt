package com.flowfoundation.wallet.page.profile

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.firebase.auth.isAnonymousSignIn
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.viewModelIOScope

class ProfileFragmentViewModel : ViewModel() {

    val profileLiveData = MutableLiveData<UserInfoData>()
    val inboxCountLiveData = MutableLiveData<Int>()

    fun load() {
        viewModelIOScope(this) {
            requestUserInfo()
//            requestInboxCount()
        }
    }

    private suspend fun requestUserInfo() {
        if (isAnonymousSignIn()) {
            return
        }
        AccountManager.userInfo()?.let { profileLiveData.postValue(it) }

        try {
            val service = retrofit().create(ApiService::class.java)
            val data = service.userInfo().data
            if (data != profileLiveData.value) {
                profileLiveData.postValue(data)
                AccountManager.updateUserInfo(data)
            }
        } catch (e: Exception) {
            loge(e)
        }
    }
}