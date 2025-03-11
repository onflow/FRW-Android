package com.flowfoundation.wallet.cache

import androidx.annotation.WorkerThread
import com.flowfoundation.wallet.page.token.custom.model.CustomTokenItem
import com.flowfoundation.wallet.utils.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object CustomTokenCacheManager {

    private val TAG = CustomTokenCacheManager::class.java.simpleName
    private val file by lazy { File(CUSTOM_TOKEN_PATH, "${"custom_token".hashCode()}") }

    @WorkerThread
    fun read(): List<CustomTokenItem>? {
        val str = file.read()
        if (str.isBlank()) {
            return null
        }

        try {
            val json = Json {
                ignoreUnknownKeys = true
            }
            return json.decodeFromString(ListSerializer(CustomTokenItem.serializer()), str)
        } catch (e: Exception) {
            loge(TAG, e)
        }
        return null
    }

    fun cache(data: List<CustomTokenItem>) {
        ioScope { cacheSync(data) }
    }

    fun cacheSync(data: List<CustomTokenItem>) {
        val str = Json.encodeToString(ListSerializer(CustomTokenItem.serializer()), data)
        str.saveToFile(file)
    }

    fun clear() {
        ioScope { file.delete() }
    }
}
