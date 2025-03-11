package com.flowfoundation.wallet.cache

import com.google.gson.Gson
import java.io.File

class TestCacheManager<T>(
    private val fileName: String,
    private val type: Class<T>,
    private val cacheDir: File
) {
    private val file by lazy { File(cacheDir, fileName) }

    fun read(): T? {
        if (!file.exists()) {
            return null
        }
        
        val str = try {
            file.readText()
        } catch (e: Exception) {
            return null
        }
        
        if (str.isBlank()) {
            return null
        }

        return try {
            Gson().fromJson(str, type)
        } catch (e: Exception) {
            null
        }
    }

    fun cacheSync(data: T) {
        val str = Gson().toJson(data)
        file.parentFile?.mkdirs() // Ensure parent directory exists
        file.writeText(str)
    }

    fun clear() {
        file.delete()
    }

    fun isCacheExist(): Boolean = file.exists() && file.length() > 0

    fun modifyTime() = file.lastModified()

    fun isExpired(duration: Long): Boolean {
        return System.currentTimeMillis() - modifyTime() > duration
    }
} 