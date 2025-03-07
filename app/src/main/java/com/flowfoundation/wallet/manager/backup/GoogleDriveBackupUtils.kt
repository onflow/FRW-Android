package com.flowfoundation.wallet.manager.backup

import android.content.Intent
import androidx.annotation.WorkerThread
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.api.services.drive.Drive
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nftco.flow.sdk.FlowAddress
import com.walletconnect.android.internal.common.crypto.sha256
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.drive.DriveServerHelper
import com.flowfoundation.wallet.manager.flowjvm.lastBlockAccount
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.getPinCode
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.secret.aesDecrypt
import com.flowfoundation.wallet.utils.secret.aesEncrypt

private const val TAG = "GoogleDriveBackupUtils"
private const val FILE_NAME = "outblock_multi_backup"

private const val AES_KEY = BuildConfig.DRIVE_AES_KEY
private val AES_PASSWORD by lazy {
    sha256(AES_KEY.toByteArray())
}

const val ACTION_GOOGLE_DRIVE_UPLOAD_FINISH = "ACTION_GOOGLE_DRIVE_UPLOAD_FINISH"
const val ACTION_GOOGLE_DRIVE_RESTORE_FINISH = "ACTION_GOOGLE_DRIVE_RESTORE_FINISH"
const val ACTION_GOOGLE_DRIVE_CHECK_FINISH = "ACTION_GOOGLE_DRIVE_CHECK_FINISH"
const val ACTION_GOOGLE_DRIVE_VIEW_FINISH = "ACTION_GOOGLE_DRIVE_VIEW_FINISH"
const val EXTRA_SUCCESS = "extra_success"
const val EXTRA_CONTENT = "extra_content"

@WorkerThread
fun uploadGoogleDriveBackup(
    driveService: Drive,
    backupCryptoProvider: BackupCryptoProvider
) {
    try {
        val driveServiceHelper = DriveServerHelper(driveService)
        val data = existingData(driveService).toMutableList()
        if (data.isEmpty()) {
            driveServiceHelper.createFile(FILE_NAME)
        }
        addData(data, backupCryptoProvider)
        driveServiceHelper.writeStringToFile(
            FILE_NAME,
            "\"${
                aesEncrypt(
                    key = AES_KEY,
                    iv = AES_PASSWORD,
                    message = Gson().toJson(data),
                )
            }\"",
        )

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

fun checkGoogleDriveBackup(
    driveService: Drive,
    provider: BackupCryptoProvider
) {
    val data = existingData(driveService).toMutableList()
    val wallet = AccountManager.get()?.wallet
    val exist = data.firstOrNull { it.userId == wallet?.id } != null
    val blockAccount = FlowAddress(wallet?.walletAddress().orEmpty()).lastBlockAccount()
    val keyExist = blockAccount?.keys?.firstOrNull { provider.getPublicKey() == it.publicKey.base16Value } != null
    LocalBroadcastManager.getInstance(Env.getApp())
        .sendBroadcast(Intent(ACTION_GOOGLE_DRIVE_CHECK_FINISH).apply {
            putExtra(EXTRA_SUCCESS, exist && keyExist)
        })
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
        val json = aesDecrypt(key = AES_KEY, iv = AES_PASSWORD, message = content)
        logd(TAG, "existingData:$json")
        Gson().fromJson(json, object : TypeToken<List<BackupItem>>() {}.type)
    } catch (e: Exception) {
        loge(e)
        throw e
    }
}

private fun addData(data: MutableList<BackupItem>, provider: BackupCryptoProvider) {
    val account = AccountManager.get() ?: throw RuntimeException("Account cannot be null")
    val wallet = account.wallet ?: throw RuntimeException("Wallet cannot be null")
    val exist = data.firstOrNull { it.userId == wallet.id }
    val blockAccount = FlowAddress(wallet.walletAddress().orEmpty()).lastBlockAccount()
    val keyIndex =
        blockAccount?.keys?.findLast { provider.getPublicKey() == it.publicKey.base16Value }?.id
    val aesKey = sha256(getPinCode().toByteArray())
    val aesIv = sha256(aesKey.toByteArray().copyOf(16).take(16).toByteArray())
    if (exist == null) {
        data.add(
            0,
            BackupItem(
                address = wallet.walletAddress() ?: "",
                userId = wallet.id,
                userName = account.userInfo.username,
                publicKey = provider.getPublicKey(),
                signAlgo = provider.getSignatureAlgorithm().index,
                hashAlgo = provider.getHashAlgorithm().index,
                keyIndex = keyIndex ?: 0,
                updateTime = System.currentTimeMillis(),
                data = aesEncrypt(key = aesKey, iv = aesIv, message = provider.getMnemonic())
            )
        )
    } else {
        exist.publicKey = provider.getPublicKey()
        exist.signAlgo = provider.getSignatureAlgorithm().index
        exist.hashAlgo = provider.getHashAlgorithm().index
        exist.keyIndex = keyIndex ?: 0
        exist.updateTime = System.currentTimeMillis()
        exist.data = aesEncrypt(key = aesKey, iv = aesIv, message = provider.getMnemonic())
    }
}

@WorkerThread
fun restoreFromGoogleDrive(driveService: Drive) {
    try {
        logd(TAG, "restoreMnemonicFromGoogleDrive")
        val data = existingData(driveService)
        LocalBroadcastManager.getInstance(Env.getApp())
            .sendBroadcast(Intent(ACTION_GOOGLE_DRIVE_RESTORE_FINISH).apply {
                putParcelableArrayListExtra(EXTRA_CONTENT, data.toCollection(ArrayList()))
            })
    } catch (e: Exception) {
        loge(e)
        sendCallback(false)
        throw e
    }
}

@WorkerThread
fun viewFromGoogleDrive(driveService: Drive) {
    try {
        logd(TAG, "restoreMnemonicFromGoogleDrive")
        val data = existingData(driveService)
        LocalBroadcastManager.getInstance(Env.getApp())
            .sendBroadcast(Intent(ACTION_GOOGLE_DRIVE_VIEW_FINISH).apply {
                putParcelableArrayListExtra(EXTRA_CONTENT, data.toCollection(ArrayList()))
            })
    } catch (e: Exception) {
        loge(e)
        sendCallback(false)
        throw e
    }
}

fun decryptMnemonic(data: String?, pinCode: String): String {
    if (data == null) {
        return ""
    }
    val aesKey = sha256(pinCode.toByteArray())
    val aesIv = sha256(aesKey.toByteArray().copyOf(16).take(16).toByteArray())
    return aesDecrypt(aesKey, aesIv, data)
}

private fun sendCallback(isSuccess: Boolean) {
    LocalBroadcastManager.getInstance(Env.getApp())
        .sendBroadcast(Intent(ACTION_GOOGLE_DRIVE_UPLOAD_FINISH).apply {
            putExtra(EXTRA_SUCCESS, isSuccess)
        })
}

