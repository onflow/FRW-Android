package com.flowfoundation.wallet.cache

import androidx.annotation.WorkerThread
import com.flowfoundation.wallet.manager.coin.CustomTokenCache
import com.flowfoundation.wallet.utils.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object CustomTokenCacheManager {

    private val TAG = CustomTokenCacheManager::class.java.simpleName
    private val file by lazy { File(CUSTOM_TOKEN_PATH, "${"custom_token".hashCode()}") }

    @WorkerThread
    fun read(): List<CustomTokenCache>? {
        val str = file.read()
        if (str.isBlank()) {
            return null
        }

        try {
            val json = Json {
                ignoreUnknownKeys = true
            }
            return json.decodeFromString(ListSerializer(CustomTokenCache.serializer()), str)
        } catch (e: Exception) {
            loge(TAG, e)
        }
        return null
    }

    fun cache(data: List<CustomTokenCache>) {
        ioScope { cacheSync(data) }
    }

    fun cacheSync(data: List<CustomTokenCache>) {
        val str = Json.encodeToString(ListSerializer(CustomTokenCache.serializer()), data)
        str.saveToFile(file)
    }

    fun clear() {
        ioScope { file.delete() }
    }

    fun isCacheExist(): Boolean = file.exists() && file.length() > 0

    fun modifyTime() = file.lastModified()

    fun isExpired(duration: Long): Boolean {
        return System.currentTimeMillis() - modifyTime() > duration
    }

}
