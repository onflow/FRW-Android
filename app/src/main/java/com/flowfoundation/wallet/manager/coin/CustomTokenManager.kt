package com.flowfoundation.wallet.manager.coin

import com.flowfoundation.wallet.cache.CustomTokenCacheManager.cacheSync
import com.flowfoundation.wallet.cache.CustomTokenCacheManager.read
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.page.token.custom.model.CustomTokenItem
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable


object CustomTokenManager {
    private val TAG = CustomTokenManager::class.java.simpleName

    private val cacheList = mutableListOf<CustomTokenCache>()

    fun init() {
        cacheList.clear()
        cacheList.addAll(read() ?: emptyList())
    }

    fun getCurrentEVMCustomTokenList(): List<CustomTokenItem> {
        return cacheList.find { it.userId == firebaseUid() }?.evmTokenList.orEmpty()
    }

    fun addEVMCustomToken(tokenItem: CustomTokenItem) {
        val userId = firebaseUid() ?: return
        cacheList.find { it.userId == userId }?.let {
            it.evmTokenList += tokenItem
        } ?: run {
            cacheList.add(CustomTokenCache(
                userId = userId,
                evmTokenList = listOf(tokenItem),
                flowTokenList = emptyList()
            ))
        }
        cacheSync(cacheList)
    }

}

@Serializable
data class CustomTokenCache(
    @SerializedName("userIdentifier")
    val userId: String,
    @SerializedName("evmTokenList")
    var evmTokenList: List<CustomTokenItem>,
    @SerializedName("flowTokenList")
    var flowTokenList: List<CustomTokenItem>
)