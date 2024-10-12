package com.flowfoundation.wallet.manager.coin

import com.flowfoundation.wallet.page.wallet.model.WalletCoinItemModel
import com.flowfoundation.wallet.utils.ioScope
import com.google.gson.annotations.SerializedName


object UserPropertyCacheManager {
    private val TAG = UserPropertyCacheManager::class.java.simpleName
    private val cacheMap = mutableMapOf<String, UserPropertyCacheData>()

    fun init() {
        ioScope {
            cacheMap.clear()
            //cacheMap.putAll()
        }
    }
}

class UserPropertyCache(
    @SerializedName("data")
    val data: Map<String, UserPropertyCacheData>
)

class UserPropertyCacheData(
    @SerializedName("flowBalance")
    val flowBalance: Map<String, String> = emptyMap(),
    @SerializedName("coinList")
    val coinList: List<WalletCoinItemModel> = emptyList()
)