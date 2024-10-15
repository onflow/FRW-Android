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

    fun cacheUserPrefix(userPrefix: UserPrefix) {
        ioScope {
            val userPrefixList = read()?.toMutableList() ?: mutableListOf()
            userPrefixList.find { it.userId == userPrefix.userId }?.let {
                it.prefix = userPrefix.prefix
            } ?: run {
                userPrefixList.add(userPrefix)
            }
            cacheSync(userPrefixList)
        }
    }

    fun cache(data: List<UserPrefix>) {
        ioScope { cacheSync(data) }
    }

    fun cacheSync(data: List<UserPrefix>) {
        val str = Json.encodeToString(ListSerializer(UserPrefix.serializer()), data)
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
