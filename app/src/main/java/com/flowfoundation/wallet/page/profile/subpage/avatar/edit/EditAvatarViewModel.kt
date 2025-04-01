package com.flowfoundation.wallet.page.profile.subpage.avatar.edit

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.cache.nftListCache
import com.flowfoundation.wallet.firebase.storage.uploadAvatarToFirebase
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.viewModelIOScope
import kotlinx.coroutines.delay

class EditAvatarViewModel : ViewModel() {
    val avatarListLiveData = MutableLiveData<List<Any>>()

    val selectedAvatarLiveData = MutableLiveData<Any>()

    val uploadResultLiveData = MutableLiveData<Boolean>()

    private var userInfo: UserInfoData? = null

    fun loadNft(userInfoData: UserInfoData) {
        this.userInfo = userInfoData
        viewModelIOScope(this) {
            val nftList = nftListCache(getNftAddress()).read()?.nfts.orEmpty().filter { !it.cover().isNullOrEmpty() }
            logd("xxx", "nftList: $nftList")
            avatarListLiveData.postValue(mutableListOf<Any>().apply {
                add(userInfoData.avatar)
                addAll(nftList)
            })
        }
    }

    fun selectAvatar(avatar: Any) {
        if (avatar != selectedAvatarLiveData.value) {
            selectedAvatarLiveData.postValue(avatar)
        }
    }

    fun uploadAvatar(bitmap: Bitmap) {
        viewModelIOScope(this) {
            if (selectedAvatarLiveData.value == null || selectedAvatarLiveData.value == userInfo?.avatar) {
                uploadResultLiveData.postValue(true)
                return@viewModelIOScope
            }
            try {
                uploadAvatarToFirebase(bitmap) { avatarUrl ->
                    logd("upload avatar url", avatarUrl)
                    viewModelIOScope(this) {
                        if (avatarUrl.isNullOrEmpty()) {
                            uploadResultLiveData.postValue(false)
                        }
                        val userInfo = AccountManager.userInfo()!!
                        val service = retrofit().create(ApiService::class.java)
                        val resp = service.updateProfile(mapOf("nickname" to userInfo.nickname, "avatar" to avatarUrl!!))
                        if (resp.status == 200) {
                            userInfo.avatar = avatarUrl
                            AccountManager.updateUserInfo(userInfo)
                            delay(200)
                        }
                        uploadResultLiveData.postValue(resp.status == 200)
                    }
                }
            } catch (e: Exception) {
                uploadResultLiveData.postValue(false)
            }
        }
    }

    private fun getNftAddress(): String {
        return WalletManager.selectedWalletAddress()
    }
}