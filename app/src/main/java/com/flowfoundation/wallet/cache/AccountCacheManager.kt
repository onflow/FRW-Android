package com.flowfoundation.wallet.cache

import androidx.annotation.WorkerThread
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.error.AccountError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object AccountCacheManager{

    private val TAG = AccountCacheManager::class.java.simpleName
    private val file by lazy { File(ACCOUNT_PATH, "${"accounts".hashCode()}") }

    @WorkerThread
    fun read(): List<Account>? {
        val str = file.read()
        logd(TAG, "read() called, returned ${str.length} characters, isBlank=${str.isBlank()}")
        if (str.isBlank()) {
            logd(TAG, "Warning: Account cache exists but is empty")
            return null
        }

        try {
            val json = Json {
                ignoreUnknownKeys = true
            }
            val result = json.decodeFromString(ListSerializer(Account.serializer()), str)
            logd(TAG, "read() returned ${result.size} accounts")
            if (result.isEmpty()) {
                logd(TAG, "Warning: Account cache exists but is empty")
            } else {
                logd(TAG, "First account username: ${result.firstOrNull()?.userInfo?.username ?: "null"}")
                logd(TAG, "First account wallet address: ${result.firstOrNull()?.wallet?.walletAddress() ?: "null"}")
                logd(TAG, "First account keystore info present: ${!result.firstOrNull()?.keyStoreInfo.isNullOrBlank()}")
            }
            return result
        } catch (e: Exception) {
            ErrorReporter.reportWithMixpanel(AccountError.DESERIALIZE_ACCOUNT_FAILED, e)
            loge(TAG, e)
        }
        return null
    }

    fun cache(data: List<Account>) {
        logd(TAG, "cache() called with ${data.size} accounts")
        if (data.isEmpty()) {
            logd(TAG, "Warning: Caching empty accounts list")
        } else {
            logd(TAG, "Caching accounts with usernames: ${data.map { it.userInfo.username }}")
        }
        ioScope { cacheSync(data) }
    }

    private fun cacheSync(data: List<Account>) {
        val str = Json.encodeToString(ListSerializer(Account.serializer()), data)
        str.saveToFile(file)
    }
}
