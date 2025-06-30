package com.flowfoundation.wallet.cache

import androidx.annotation.WorkerThread
import com.flowfoundation.wallet.manager.token.DisplayTokenListCache
import com.flowfoundation.wallet.utils.DISPLAY_TOKEN_PATH
import com.flowfoundation.wallet.utils.error.AccountError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.read
import com.flowfoundation.wallet.utils.saveToFile
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

object DisplayTokenCacheManager {

    private val TAG = DisplayTokenCacheManager::class.java.simpleName
    private val file by lazy { File(DISPLAY_TOKEN_PATH, "${"display_token_cache".hashCode()}") }

    @WorkerThread
    fun read(): Map<String, DisplayTokenListCache>? {
        val str = file.read()
        if (str.isBlank()) {
            return null
        }

        try {
            val json = Json {
                ignoreUnknownKeys = true
            }
            return json.decodeFromString(MapSerializer(String.serializer(), DisplayTokenListCache.serializer()), str)
        } catch (e: Exception) {
            ErrorReporter.reportWithMixpanel(AccountError.DESERIALIZE_DISPLAY_TOKEN_FAILED, e)
            loge(TAG, e)
        }
        return null
    }

    fun cache(data: Map<String, DisplayTokenListCache>) {
        ioScope {
            cacheSync(data)
        }
    }

    fun cacheSync(data: Map<String, DisplayTokenListCache>) {
        val str = Json.encodeToString(MapSerializer(String.serializer(), DisplayTokenListCache.serializer()), data)
        str.saveToFile(file)
    }

} 