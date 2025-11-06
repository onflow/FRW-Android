package com.flowfoundation.wallet.cache

import androidx.annotation.WorkerThread
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountWalletManager
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.error.AccountError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object AccountCacheManager{

    private val TAG = AccountCacheManager::class.java.simpleName
    private val file by lazy { File(ACCOUNT_PATH, "${"accounts".hashCode()}") }
    private val backupFile by lazy { File(ACCOUNT_PATH, "${"accounts_backup".hashCode()}") }

    @WorkerThread
    fun read(): List<Account>? {
        logd(TAG, "read() called")

        // Try primary cache first
        val primaryResult = readFromFile(file)
        if (primaryResult != null) {
            logd(TAG, "Successfully read from primary cache: ${primaryResult.size} accounts")
            // Update backup if primary is good
            ioScope {
                try {
                    backupFile.writeText(file.readText())
                    logd(TAG, "Updated backup from validated primary cache")
                } catch (e: Exception) {
                    loge(TAG, "Failed to update backup: $e")
                }
            }
            return primaryResult
        }

        // Try backup cache if primary fails
        logd(TAG, "Primary cache failed, trying backup")
        val backupResult = readFromFile(backupFile)
        if (backupResult != null) {
            logd(TAG, "Successfully recovered from backup cache: ${backupResult.size} accounts")
            // Restore primary from backup
            ioScope {
                try {
                    file.writeText(backupFile.readText())
                    logd(TAG, "Restored primary from repaired backup cache")
                } catch (e: Exception) {
                    loge(TAG, "Failed to restore primary from backup: $e")
                }
            }
            return backupResult
        }

        logd(TAG, "Both primary and backup cache failed, even with repair attempts")
        return null
    }

    private fun readFromFile(cacheFile: File): List<Account>? {
        if (!cacheFile.exists()) {
            logd(TAG, "Cache file does not exist: ${cacheFile.name}")
            return null
        }

        val str = cacheFile.read()
        logd(TAG, "Reading from ${cacheFile.name}: ${str.length} characters, isBlank=${str.isBlank()}")

        if (str.isBlank()) {
            logd(TAG, "Warning: Cache file ${cacheFile.name} exists but is empty")
            return null
        }

        try {
            val json = Json {
                ignoreUnknownKeys = true
            }
            val result = json.decodeFromString(ListSerializer(Account.serializer()), str)
            logd(TAG, "Successfully decoded ${result.size} accounts from ${cacheFile.name}")

            if (result.isEmpty()) {
                logd(TAG, "Warning: Cache file ${cacheFile.name} contains empty account list")
                return null
            }

            // Validate account data
            val validAccounts = result.filter { account ->
                val isValid = account.userInfo.username.isNotBlank() &&
                             (!account.keyStoreInfo.isNullOrBlank() || !account.prefix
                                 .isNullOrBlank() || AccountWalletManager.isHDWallet(account.wallet?.id ?: ""))
                if (!isValid) {
                    logd(TAG, "Invalid account found: ${account.userInfo.username}")
                }
                isValid
            }

            if (validAccounts.size != result.size) {
                logd(TAG, "Filtered out ${result.size - validAccounts.size} invalid accounts")
            }

            if (validAccounts.isNotEmpty()) {
                logd(TAG, "Returning ${validAccounts.size} valid accounts")
                logd(TAG, "First account username: ${validAccounts.firstOrNull()?.userInfo?.username}")
                logd(TAG, "First account wallet address: ${validAccounts.firstOrNull()?.wallet?.walletAddress()}")
                logd(TAG, "First account keystore info present: ${!validAccounts.firstOrNull()?.keyStoreInfo.isNullOrBlank()}")
            }

            return validAccounts
        } catch (e: Exception) {
            ErrorReporter.reportWithMixpanel(AccountError.DESERIALIZE_ACCOUNT_FAILED, e)
            loge(TAG, "Error reading from ${cacheFile.name}: $e")
            loge(TAG, "File content preview (first 200 chars): ${str.take(200)}")
            if (str.length > 200) {
                loge(TAG, "File content preview (last 200 chars): ${str.takeLast(200)}")
            }
            loge(TAG, "File size: ${str.length} characters, File path: ${cacheFile.absolutePath}")
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
        ioScope {
            try {
                cacheSync(data)
                // Create backup copy only after verifying main file integrity
                if (file.exists() && file.length() > 0) {
                    // Verify the written data is valid before creating backup
                    val writtenData = readFromFile(file)
                    if (writtenData != null && writtenData.size == data.size) {
                        backupFile.writeText(file.readText())
                        logd(TAG, "Created backup copy of validated account cache")
                    } else {
                        loge(TAG, "Main cache validation failed, not creating backup. Expected: ${data.size}, Got: ${writtenData?.size}")
                    }
                }
            } catch (e: Exception) {
                loge(TAG, "Error caching accounts: $e")
            }
        }
    }

    private fun cacheSync(data: List<Account>) {
        val str = Json.encodeToString(ListSerializer(Account.serializer()), data)

        // Validate JSON before writing
        try {
            Json.decodeFromString(ListSerializer(Account.serializer()), str)
        } catch (e: Exception) {
            loge(TAG, "Generated invalid JSON, not writing to cache: $e")
            return
        }

        str.saveToFile(file)
        logd(TAG, "Successfully cached ${data.size} accounts")
    }

    fun clearCache() {
        ioScope {
            try {
                file.delete()
                backupFile.delete()
                logd(TAG, "Cleared account cache and backup")
            } catch (e: Exception) {
                loge(TAG, "Error clearing cache: $e")
            }
        }
    }
}
