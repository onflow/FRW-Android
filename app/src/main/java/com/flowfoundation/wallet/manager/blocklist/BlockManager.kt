package com.flowfoundation.wallet.manager.blocklist

import android.net.Uri
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loge
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URL
import java.util.concurrent.TimeUnit

object BlockManager {

    private val TAG = BlockManager::class.java.simpleName
    private const val BLOCKLIST_URL = "https://flow-blocklist.vercel.app/api/domain"
    private const val CACHE_EXPIRE_TIME_HOURS = 2L

    private var blockList: Set<String> = emptySet()
    private var lastFetchTime: Long = 0L

    private val mutex = Mutex()

    fun initialize() {
        ioScope {
            refreshBlockListIfNeeded(forceRefresh = true)
        }
    }

    suspend fun isBlocked(url: String): Boolean {
        refreshBlockListIfNeeded()

        try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return false

            return mutex.withLock {
                blockList.any { blockedDomain ->
                    host == blockedDomain || host.matches(Regex("^[\\w.-]+\\.$blockedDomain$"))
                }
            }
        } catch (e: Exception) {
            loge(e)
            return false
        }
    }

    private suspend fun refreshBlockListIfNeeded(forceRefresh: Boolean = false) {
        val needRefresh = mutex.withLock {
            val currentTime = System.currentTimeMillis()
            val isExpired = currentTime - lastFetchTime > TimeUnit.HOURS.toMillis(CACHE_EXPIRE_TIME_HOURS)
            forceRefresh || isExpired || blockList.isEmpty()
        }

        if (needRefresh) {
            fetchBlockList()
        }
    }

    private fun fetchBlockList() {
        ioScope {
            try {
                val connection = URL(BLOCKLIST_URL).openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                val text = connection.getInputStream().bufferedReader().use { it.readText() }
                val response = Gson().fromJson(text, BlockListResponse::class.java)
                val mergedList = mutableSetOf<String>()
                
                response.flow?.let { flowList ->
                    mergedList.addAll(flowList)
                }
                
                response.evm?.let { evmList ->
                    mergedList.addAll(evmList)
                }
                mutex.withLock {
                    blockList = mergedList
                    lastFetchTime = System.currentTimeMillis()
                    loge(TAG, "Block list updated, total domains: ${blockList.size}")
                }
            } catch (e: Exception) {
                loge(TAG, "Failed to fetch block list: ${e.message}")
            }
        }
    }
}

data class BlockListResponse(
    @SerializedName("flow")
    val flow: List<String>?,
    @SerializedName("evm")
    val evm: List<String>?
)