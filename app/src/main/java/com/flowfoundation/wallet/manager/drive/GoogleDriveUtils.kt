package com.flowfoundation.wallet.manager.drive

import android.content.Intent
import androidx.annotation.WorkerThread
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.api.services.drive.Drive
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.firebase.auth.firebaseUid
import com.flowfoundation.wallet.manager.account.username
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.secret.aesDecrypt
import com.flowfoundation.wallet.utils.secret.aesEncrypt
import com.flowfoundation.wallet.wallet.Wallet


private const val TAG = "GoogleDriveUtils"

private const val FILE_NAME = "outblock_backup"

const val ACTION_GOOGLE_DRIVE_UPLOAD_FINISH = "ACTION_GOOGLE_DRIVE_UPLOAD_FINISH"
const val ACTION_GOOGLE_DRIVE_DELETE_FINISH = "ACTION_GOOGLE_DRIVE_DELETE_FINISH"
const val ACTION_GOOGLE_DRIVE_RESTORE_FINISH = "ACTION_GOOGLE_DRIVE_RESTORE_FINISH"
const val ACTION_GOOGLE_DRIVE_LOGIN_FINISH = "ACTION_GOOGLE_DRIVE_LOGIN_FINISH"
const val EXTRA_SUCCESS = "extra_success"
const val EXTRA_CONTENT = "extra_content"

private const val AES_KEY = BuildConfig.DRIVE_AES_KEY

@WorkerThread
suspend fun uploadMnemonicToGoogleDrive(driveService: Drive, password: String) {
    try {
        logd(TAG, "uploadMnemonicToGoogleDrive")
        val driveServiceHelper = DriveServerHelper(driveService)
        val data = existingData(driveService).toMutableList()
        if (data.isEmpty()) {
            driveServiceHelper.createFile(FILE_NAME)
        }

        addData(data, password)

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

@WorkerThread
fun restoreMnemonicFromGoogleDrive(driveService: Drive) {
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
fun deleteMnemonicFromGoogleDrive(driveService: Drive) {
    try {
        logd(TAG, "deleteMnemonicFromGoogleDrive")
        val driveServiceHelper = DriveServerHelper(driveService)
        val data = existingData(driveService).toMutableList()
        if (data.isNotEmpty()) {
            val username = username()
            data.removeIf { it.username == username }

            driveServiceHelper.writeStringToFile(FILE_NAME, "\"${aesEncrypt(AES_KEY, message = Gson().toJson(data))}\"")

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

private fun existingData(driveService: Drive): List<DriveItem> {
    val driveServiceHelper = DriveServerHelper(driveService)
    val fileId = driveServiceHelper.getFileId(FILE_NAME) ?: return emptyList()

    if (BuildConfig.DEBUG) {
        driveServiceHelper.fileList()?.files?.map {
            logd(TAG, "file list:${it.name}")
//            driveServiceHelper.deleteFile(it.id)
        }
    }

    return try {
        logd(TAG, "existingData fileId:$fileId")
        val content = driveServiceHelper.readFile(fileId).second.trim { it == '"' }
        logd(TAG, "existingData content:$content")
        val json = aesDecrypt(AES_KEY, message = content)
        logd(TAG, "existingData:$json")
        Gson().fromJson(json, object : TypeToken<List<DriveItem>>() {}.type)
    } catch (e: Exception) {
        loge(e)
        throw e
    }
}

private fun addData(data: MutableList<DriveItem>, password: String) {
    val username = username()

    if (username.isBlank()) {
        throw RuntimeException("username is empty")
    }

    val exist = data.firstOrNull { it.username == username }
    if (exist == null) {
        val uid = firebaseUid() ?: throw RuntimeException("uid is empty")
        data.add(
            0,
            DriveItem(username, uid = uid, version = BuildConfig.VERSION_NAME, data = aesEncrypt(password, message = Wallet.store().mnemonic()))
        )
    } else {
        exist.version = BuildConfig.VERSION_NAME
        exist.data = aesEncrypt(password, message = Wallet.store().mnemonic())
    }
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