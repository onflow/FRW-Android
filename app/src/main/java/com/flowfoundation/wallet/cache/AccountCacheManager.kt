package com.flowfoundation.wallet.cache

import androidx.annotation.WorkerThread
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.utils.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object AccountCacheManager{

    private val TAG = AccountCacheManager::class.java.simpleName
    private val file by lazy { File(ACCOUNT_PATH, "${"accounts".hashCode()}") }

    @WorkerThread
    fun read(): List<Account>? {
        val str = file.read()
        if (str.isBlank()) {
            return null
        }

        try {
            val json = Json {
                ignoreUnknownKeys = true
            }
            return json.decodeFromString(ListSerializer(Account.serializer()), str)
        } catch (e: Exception) {
            loge(TAG, e)
        }
        return null
    }

    fun cache(data: List<Account>) {
        ioScope { cacheSync(data) }
    }

    private fun cacheSync(data: List<Account>) {
        val str = Json.encodeToString(ListSerializer(Account.serializer()), data)
        str.saveToFile(file)
    }
}
