package com.flowfoundation.wallet.cache

import androidx.annotation.WorkerThread
import com.flowfoundation.wallet.manager.account.UserPrefix
import com.flowfoundation.wallet.utils.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object UserPrefixCacheManager{

    private val TAG = UserPrefixCacheManager::class.java.simpleName
    private val file by lazy { File(USER_PREFIX_PATH, "${"user_prefix".hashCode()}") }

    @WorkerThread
    fun read(): List<UserPrefix>? {
        val str = file.read()
        if (str.isBlank()) {
            return null
        }

        try {
            val json = Json {
                ignoreUnknownKeys = true
            }
            return json.decodeFromString(ListSerializer(UserPrefix.serializer()), str)
        } catch (e: Exception) {
            loge(TAG, e)
        }
        return null
    }

    fun cache(data: List<UserPrefix>) {
        ioScope { cacheSync(data) }
    }

    private fun cacheSync(data: List<UserPrefix>) {
        val str = Json.encodeToString(ListSerializer(UserPrefix.serializer()), data)
        str.saveToFile(file)
    }

    fun clear() {
        ioScope { file.delete() }
    }
}
