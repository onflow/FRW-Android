package com.flowfoundation.wallet.page.profile.subpage.wallet

import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.storageInfoCache
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.executeCadence
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import kotlinx.serialization.Serializable
import org.onflow.flow.infrastructure.Cadence


fun queryStorageInfo() {
    ioScope {
        val address = WalletManager.wallet()?.walletAddress()
        if (address.isNullOrEmpty()) {
            return@ioScope
        }
        
        var retryCount = 0
        val maxRetries = 3
        
        while (retryCount <= maxRetries) {
            try {
                val response = CadenceScript.CADENCE_QUERY_STORAGE_INFO.executeCadence {
                    arg { Cadence.address(address) }
                }?.decode<StorageInfo>()
                
                if (response == null) {
                    if (retryCount < maxRetries) {
                        retryCount++
                        logd("StorageInfo", "Storage info query returned null, retrying... (attempt $retryCount)")
                        kotlinx.coroutines.delay(2000L * retryCount) // Exponential backoff
                        continue
                    } else {
                        logd("StorageInfo", "Storage info query failed after $maxRetries retries")
                        return@ioScope
                    }
                }
                
                storageInfoCache().cache(response)
                logd("StorageInfo", "Storage info successfully cached")
                return@ioScope
                
            } catch (e: Exception) {
                logd("StorageInfo", "Error querying storage info (attempt ${retryCount + 1}): ${e.message}")
                
                if (retryCount < maxRetries) {
                    retryCount++
                    val delayMs = 2000L * retryCount // 2s, 4s, 6s
                    logd("StorageInfo", "Retrying storage info query in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                } else {
                    logd("StorageInfo", "Storage info query failed after $maxRetries retries: ${e.message}")
                    break
                }
            }
        }
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