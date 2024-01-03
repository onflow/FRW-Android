package io.outblock.lilico.manager.backup

import android.content.Intent
import androidx.annotation.WorkerThread
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.api.services.drive.Drive
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.walletconnect.android.internal.common.crypto.sha256
import io.outblock.lilico.BuildConfig
import io.outblock.lilico.firebase.auth.firebaseUid
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.manager.drive.DriveServerHelper
import io.outblock.lilico.manager.env.EnvKey
import io.outblock.lilico.utils.Env
import io.outblock.lilico.utils.logd
import io.outblock.lilico.utils.loge
import io.outblock.lilico.utils.secret.aesDecrypt
import io.outblock.lilico.utils.secret.aesEncrypt

private const val TAG = "GoogleDriveBackupUtils"
private const val FILE_NAME = "outblock_google_backup"

private val AES_KEY by lazy { EnvKey.get("DRIVE_AES_KEY") }
private val AES_PASSWORD by lazy {
    sha256(AES_KEY.toByteArray())
}

const val ACTION_GOOGLE_DRIVE_UPLOAD_FINISH = "ACTION_GOOGLE_DRIVE_UPLOAD_FINISH"
const val ACTION_GOOGLE_DRIVE_DELETE_FINISH = "ACTION_GOOGLE_DRIVE_DELETE_FINISH"
const val ACTION_GOOGLE_DRIVE_RESTORE_FINISH = "ACTION_GOOGLE_DRIVE_RESTORE_FINISH"
const val EXTRA_SUCCESS = "extra_success"
const val EXTRA_CONTENT = "extra_content"

@WorkerThread
suspend fun uploadGoogleDriveBackup(driveService: Drive, backupCryptoProvider: BackupCryptoProvider) {
    try {
        val driveServiceHelper = DriveServerHelper(driveService)
        val data = existingData(driveService).toMutableList()
        if (data.isEmpty()) {
            driveServiceHelper.createFile(FILE_NAME)
        }
        addData(data, backupCryptoProvider)
        driveServiceHelper.writeStringToFile(FILE_NAME, "\"${aesEncrypt(AES_KEY, message = Gson().toJson(data))}\"")

        if (BuildConfig.DEBUG) {
            val readText = driveServiceHelper.readFile(driveServiceHelper.getFileId(FILE_NAME)!!)
            logd(TAG, "readText:$readText")
        }
        sendCallback(true)
    } catch (e: Exception) {
        loge(e)
        sendCallback(false)
        throw e
    }
}

private fun existingData(driveService: Drive): List<BackupItem> {
    val driveServiceHelper = DriveServerHelper(driveService)
    val fileId = driveServiceHelper.getFileId(FILE_NAME) ?: return emptyList()

    if (BuildConfig.DEBUG) {
        driveServiceHelper.fileList()?.files?.map {
            logd(TAG, "file list:${it.name}")
        }
    }

    return try {
        logd(TAG, "existingData fileId:$fileId")
        val content = driveServiceHelper.readFile(fileId).second.trim { it == '"' }
        logd(TAG, "existingData content:$content")
        val json = aesDecrypt(AES_KEY, message = content)
        logd(TAG, "existingData:$json")
        Gson().fromJson(json, object : TypeToken<List<BackupItem>>() {}.type)
    } catch (e: Exception) {
        loge(e)
        emptyList()
    }
}

private fun addData(data: MutableList<BackupItem>, provider: BackupCryptoProvider) {
    val account = AccountManager.get() ?: throw RuntimeException("Account cannot be null")
    val wallet = account.wallet ?: throw RuntimeException("Wallet cannot be null")
    val exist = data.firstOrNull { it.userId == wallet.id }
    if (exist == null) {
        data.add(
            0,
            BackupItem(
                address = wallet.walletAddress() ?: "",
                userId = wallet.id,
                userName = account.userInfo.username,
                userAvatar = account.userInfo.avatar,
                publicKey = provider.getPublicKey(),
                signAlgo = provider.getSignatureAlgorithm().index,
                hashAlgo = provider.getHashAlgorithm().index,
                updateTime = System.currentTimeMillis(),
                data = aesEncrypt(AES_PASSWORD, AES_PASSWORD, message = provider.getMnemonic())
            )
        )
    } else {
        exist.publicKey = provider.getPublicKey()
        exist.signAlgo = provider.getSignatureAlgorithm().index
        exist.hashAlgo = provider.getHashAlgorithm().index
        exist.updateTime = System.currentTimeMillis()
        exist.data = aesEncrypt(AES_PASSWORD, AES_PASSWORD, message = provider.getMnemonic())
    }
}

@WorkerThread
fun restoreFromGoogleDrive(driveService: Drive) {
    try {
        logd(TAG, "restoreMnemonicFromGoogleDrive")
        val data = existingData(driveService)
        LocalBroadcastManager.getInstance(Env.getApp()).sendBroadcast(Intent(ACTION_GOOGLE_DRIVE_RESTORE_FINISH).apply {
            putParcelableArrayListExtra(EXTRA_CONTENT, data.toCollection(ArrayList()))
        })
    } catch (e: Exception) {
        loge(e)
        sendCallback(false)
        throw e
    }
}


@WorkerThread
fun deleteFromGoogleDrive(driveService: Drive) {
    try {
        logd(TAG, "deleteMnemonicFromGoogleDrive")
        val driveServiceHelper = DriveServerHelper(driveService)
        val data = existingData(driveService).toMutableList()
        if (data.isNotEmpty()) {
            data.removeIf { it.userId == firebaseUid() }

            driveServiceHelper.writeStringToFile(
                FILE_NAME, "\"${aesEncrypt(
                    AES_KEY, message = Gson().toJson(data))}\"")

            if (BuildConfig.DEBUG) {
                val readText = driveServiceHelper.readFile(driveServiceHelper.getFileId(FILE_NAME)!!)
                logd(TAG, "readText:$readText")
            }
        }
        sendDeleteCallback(true)
    } catch (e: Exception) {
        loge(e)
        sendDeleteCallback(false)
        throw e
    }
}

fun decryptMnemonic(data: String?): String {
    if (data == null) {
        return ""
    }
    return aesDecrypt(AES_PASSWORD, AES_PASSWORD, data)
}

private fun sendCallback(isSuccess: Boolean) {
    LocalBroadcastManager.getInstance(Env.getApp()).sendBroadcast(Intent(ACTION_GOOGLE_DRIVE_UPLOAD_FINISH).apply {
        putExtra(EXTRA_SUCCESS, isSuccess)
    })
}

private fun sendDeleteCallback(isSuccess: Boolean) {
    LocalBroadcastManager.getInstance(Env.getApp()).sendBroadcast(Intent(ACTION_GOOGLE_DRIVE_DELETE_FINISH).apply {
        putExtra(EXTRA_SUCCESS, isSuccess)
    })
}
