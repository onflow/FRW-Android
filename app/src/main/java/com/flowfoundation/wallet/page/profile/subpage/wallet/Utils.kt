package com.flowfoundation.wallet.page.profile.subpage.wallet

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.storageInfoCache
import com.flowfoundation.wallet.manager.flowjvm.Cadence
import com.flowfoundation.wallet.manager.flowjvm.executeCadence
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.extensions.toSafeLong
import com.flowfoundation.wallet.utils.ioScope


fun queryStorageInfo() {
    ioScope {
        val address = WalletManager.wallet()?.walletAddress()
        if (address.isNullOrEmpty()) {
            return@ioScope
        }
        val response = Cadence.CADENCE_QUERY_STORAGE_INFO.executeCadence {
            arg { address(address) }
        }
        if (response?.stringValue.isNullOrBlank()) {
            return@ioScope
        }
        val data = Gson().fromJson(response?.stringValue, StorageInfoResult::class.java)
        val info = StorageInfo(
            data.getValueByName("available"),
            data.getValueByName("used"),
            data.getValueByName("capacity"),
        )
        storageInfoCache().cache(info)
    }
}

private fun StorageInfoResult.getValueByName(name: String) =
    this.value?.find { it.key?.value == name }?.value?.value.toSafeLong()


data class StorageInfoResult(
    @SerializedName("type")
    val type: String?,
    @SerializedName("value")
    val value: List<Item>?
) {
    data class Item(
        @SerializedName("key")
        val key: Value?,
        @SerializedName("value")
        val value: Value?
    ) {
        data class Value(
            @SerializedName("type")
            val type: String?,
            @SerializedName("value")
            val value: String?
        )
    }
}

data class StorageInfo(
    @SerializedName("available")
    val available: Long,
    @SerializedName("used")
    val used: Long,
    @SerializedName("capacity")
    val capacity: Long,
)