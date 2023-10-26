package io.outblock.lilico.page.profile.subpage.wallet

import com.nftco.flow.sdk.decode
import io.outblock.lilico.cache.storageInfoCache
import io.outblock.lilico.manager.flowjvm.CADENCE_QUERY_STORAGE_INFO
import io.outblock.lilico.manager.flowjvm.executeCadence
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.utils.ioScope
import kotlinx.serialization.Serializable


fun queryStorageInfo() {
    ioScope {
        val address = WalletManager.selectedWalletAddress()
        if (address.isEmpty()) {
            return@ioScope
        }
        val response = CADENCE_QUERY_STORAGE_INFO.executeCadence {
            arg { address(address) }
        }

        val info = response?.decode<StorageInfo>() ?: return@ioScope

        storageInfoCache().cache(info)
    }
}

@Serializable
data class StorageInfo(
    val available: Long,
    val used: Long,
    val capacity: Long,
)