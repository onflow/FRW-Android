package com.flowfoundation.wallet.cache

import androidx.annotation.WorkerThread
import com.google.gson.Gson
import com.flowfoundation.wallet.utils.*
import java.io.File
import java.lang.reflect.Type

class CacheManager<T>(
    private val fileName: String,
    private val type: Type,
    private val cacheDir: File = CACHE_PATH
) {

    private val file by lazy { File(cacheDir, fileName) }

    @WorkerThread
    fun read(): T? {
        val str = file.read()
        if (str.isBlank()) {
            return null
        }

        return try {
            return Gson().fromJson(str, type)
        } catch (e: Exception) {
            loge(TAG, "$e \n ::JSON:: $type")
            null
        }
    }

    fun cache(data: T) {
        ioScope { cacheSync(data) }
    }

    fun cacheSync(data: T) {
        val str = Gson().toJson(data)
        str.saveToFile(file)
    }

    fun clear() {
        ioScope { file.delete() }
    }

    fun isCacheExist(): Boolean = file.exists() && file.length() > 0

    private fun modifyTime() = file.lastModified()

    fun isExpired(duration: Long): Boolean {
        return System.currentTimeMillis() - modifyTime() > duration
    }

    companion object {
        private val TAG = CacheManager::class.java.simpleName
    }
}