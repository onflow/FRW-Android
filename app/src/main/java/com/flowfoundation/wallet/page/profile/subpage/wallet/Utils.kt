package com.flowfoundation.wallet.page.profile.subpage.wallet

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.storageInfoCache
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.executeCadence
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.utils.ioScope
import kotlinx.serialization.Serializable
import org.onflow.flow.infrastructure.Cadence


fun queryStorageInfo() {
    ioScope {
        val address = WalletManager.wallet()?.walletAddress()
        if (address.isNullOrEmpty()) {
            return@ioScope
        }
        val response = CadenceScript.CADENCE_QUERY_STORAGE_INFO.executeCadence {
            arg { Cadence.address(address) }
        }?.decode<StorageInfo>()
        if (response == null) {
            return@ioScope
        }
        storageInfoCache().cache(response)
    }
}

@Serializable
data class StorageInfo(
    @SerializedName("available")
    val available: Long,
    @SerializedName("used")
    val used: Long,
    @SerializedName("capacity")
    val capacity: Long,
)