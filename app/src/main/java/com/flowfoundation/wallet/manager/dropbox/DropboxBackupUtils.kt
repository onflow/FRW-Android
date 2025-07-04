package com.flowfoundation.wallet.manager.dropbox

import android.content.Intent
import androidx.annotation.WorkerThread
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dropbox.core.v2.DbxClientV2
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.manager.backup.BackupItem
import com.flowfoundation.wallet.manager.flowjvm.lastBlockAccount
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.error.DropboxBackupError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.getPinCode
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.utils.secret.aesDecrypt
import com.flowfoundation.wallet.utils.secret.aesEncrypt
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.reown.android.internal.common.crypto.sha256
import org.onflow.flow.models.FlowAddress


private const val TAG = "DropboxBackupUtils"
private const val FILE_NAME = "outblock_multi_backup"

private const val AES_KEY = BuildConfig.DRIVE_AES_KEY
private val AES_PASSWORD by lazy {
    sha256(AES_KEY.toByteArray())
}

const val ACTION_DROPBOX_UPLOAD_FINISH = "ACTION_DROPBOX_UPLOAD_FINISH"
const val ACTION_DROPBOX_RESTORE_FINISH = "ACTION_DROPBOX_RESTORE_FINISH"
const val ACTION_DROPBOX_CHECK_FINISH = "ACTION_DROPBOX_CHECK_FINISH"
const val ACTION_DROPBOX_VIEW_FINISH = "ACTION_DROPBOX_VIEW_FINISH"
const val ACTION_DROPBOX_LOGIN_FINISH = "ACTION_DROPBOX_LOGIN_FINISH"
const val EXTRA_SUCCESS = "extra_success"
const val EXTRA_CONTENT = "extra_content"

@WorkerThread
suspend fun uploadDropboxBackup(
    dropboxClient: DbxClientV2,
    backupCryptoProvider: BackupCryptoProvider
) {
    try {
        val dropboxHelper = DropboxServerHelper(dropboxClient)
        val data = existingData(dropboxHelper).toMutableList()
        if (data.isEmpty()) {
            dropboxHelper.createFile(FILE_NAME)
        }
        addData(data, backupCryptoProvider)
        dropboxHelper.writeStringToFile(
            FILE_NAME,
            "\"${
                aesEncrypt(
                    key = AES_KEY,
                    iv = AES_PASSWORD,
                    message = Gson().toJson(data),
                )
            }\""
        )

        if (BuildConfig.DEBUG) {
            val readText = dropboxHelper.readFile(dropboxHelper.getReadFilePath(FILE_NAME))
            logd(TAG, "readText:$readText")
        }
        sendCallback(true)
    } catch (e: Exception) {
        loge(e)
        ErrorReporter.reportWithMixpanel(DropboxBackupError.UPLOAD_BACKUP_ERROR, e)
        sendCallback(false)
        throw e
    }
}

suspend fun checkDropboxBackup(
    dropboxClient: DbxClientV2,
    provider: BackupCryptoProvider
) {
    val dropboxHelper = DropboxServerHelper(dropboxClient)
    val data = existingData(dropboxHelper).toMutableList()
    val wallet = AccountManager.get()?.wallet
    val exist = data.firstOrNull { it.userId == wallet?.id } != null
    val blockAccount = FlowAddress(wallet?.walletAddress().orEmpty()).lastBlockAccount()
    
    // Normalize public keys for comparison - remove prefixes and convert to lowercase
    val providerPubKey = provider.getPublicKey().removePrefix("0x").removePrefix("04").lowercase()
    
    val keyExist = blockAccount.keys?.firstOrNull { key ->
        val onChainPubKey = key.publicKey.removePrefix("0x").removePrefix("04").lowercase()
        providerPubKey == onChainPubKey
    } != null
    
    LocalBroadcastManager.getInstance(Env.getApp())
        .sendBroadcast(Intent(ACTION_DROPBOX_CHECK_FINISH).apply {
            putExtra(EXTRA_SUCCESS, exist && keyExist)
        })
}

private fun existingData(dropboxHelper: DropboxServerHelper): List<BackupItem> {
    val filePath = dropboxHelper.getReadFilePath(FILE_NAME)
    if (filePath.isEmpty()) {
        return emptyList()
    }
    if (BuildConfig.DEBUG) {
        logd(TAG, "file list:${dropboxHelper.allFileList().joinToString(separator = ", ")}")
    }

    return try {
        logd(TAG, "existingData filePath:$filePath")
        val contentList = dropboxHelper.readFile(filePath)
        logd(TAG, "existingData content:$contentList")
        val backupItems = contentList.flatMap { pair ->
            val content = pair.second.trim { it == '"' }
            if (content.isNotEmpty()) {
                val json = aesDecrypt(key = AES_KEY, iv = AES_PASSWORD, message = content)
                logd(TAG, "existingData decrypted content:$json")
                Gson().fromJson(json, object : TypeToken<List<BackupItem>>() {}.type)
            } else {
                emptyList<BackupItem>()
            }
        }
        backupItems.distinct()
    } catch (e: Exception) {
        ErrorReporter.reportWithMixpanel(DropboxBackupError.READ_FILE_ERROR, e)
        loge(e)
        return emptyList()
    }
}

private suspend fun addData(data: MutableList<BackupItem>, provider: BackupCryptoProvider) {
    val account = AccountManager.get() ?: throw RuntimeException("Account cannot be null")
    val wallet = account.wallet ?: throw RuntimeException("Wallet cannot be null")
    val exist = data.firstOrNull { it.userId == wallet.id }
    val blockAccount = FlowAddress(wallet.walletAddress().orEmpty()).lastBlockAccount()
    
    // Normalize public keys for comparison - remove prefixes and convert to lowercase
    val providerPubKey = provider.getPublicKey().removePrefix("0x").removePrefix("04").lowercase()
    
    val keyIndex = blockAccount.keys?.findLast { key ->
        val onChainPubKey = key.publicKey.removePrefix("0x").removePrefix("04").lowercase()
        providerPubKey == onChainPubKey
    }?.index
    
    val aesKey = sha256(getPinCode().toByteArray())
    val aesIv = sha256(aesKey.toByteArray().copyOf(16).take(16).toByteArray())
    if (exist == null) {
        if (keyIndex != null) {
            data.add(
                0,
                BackupItem(
                    address = wallet.walletAddress() ?: "",
                    userId = wallet.id,
                    userName = account.userInfo.username,
                    publicKey = provider.getPublicKey(),
                    signAlgo = provider.getSignatureAlgorithm().cadenceIndex,
                    hashAlgo = provider.getHashAlgorithm().cadenceIndex,
                    keyIndex = keyIndex.toInt(),
                    updateTime = System.currentTimeMillis(),
                    data = aesEncrypt(key = aesKey, iv = aesIv, message = provider.getMnemonic())
                )
            )
        }
    } else {
        exist.publicKey = provider.getPublicKey()
        exist.signAlgo = provider.getSignatureAlgorithm().cadenceIndex
        exist.hashAlgo = provider.getHashAlgorithm().cadenceIndex
        if (keyIndex != null) {
            exist.keyIndex = keyIndex.toInt()
        }
        exist.updateTime = System.currentTimeMillis()
        exist.data = aesEncrypt(key = aesKey, iv = aesIv, message = provider.getMnemonic())
    }
}

@WorkerThread
fun restoreFromDropbox(dropboxClient: DbxClientV2) {
    try {
        logd(TAG, "restoreMnemonicFromDropbox")
        val dropboxHelper = DropboxServerHelper(dropboxClient)
        val data = existingData(dropboxHelper)
        logw("Dropbox", "broadcast send")
        LocalBroadcastManager.getInstance(Env.getApp())
            .sendBroadcast(Intent(ACTION_DROPBOX_RESTORE_FINISH).apply {
                putParcelableArrayListExtra(EXTRA_CONTENT, data.toCollection(ArrayList()))
            })
    } catch (e: Exception) {
        loge(e)
        sendCallback(false)
        throw e
    }
}

@WorkerThread
fun viewFromDropbox(dropboxClient: DbxClientV2) {
    try {
        logd(TAG, "viewMnemonicFromDropbox")
        val dropboxHelper = DropboxServerHelper(dropboxClient)
        val data = existingData(dropboxHelper)
        LocalBroadcastManager.getInstance(Env.getApp())
            .sendBroadcast(Intent(ACTION_DROPBOX_VIEW_FINISH).apply {
                putParcelableArrayListExtra(EXTRA_CONTENT, data.toCollection(ArrayList()))
            })
    } catch (e: Exception) {
        loge(e)
        sendCallback(false)
        throw e
    }
}

private fun sendCallback(isSuccess: Boolean) {
    LocalBroadcastManager.getInstance(Env.getApp())
        .sendBroadcast(Intent(ACTION_DROPBOX_UPLOAD_FINISH).apply {
            putExtra(EXTRA_SUCCESS, isSuccess)
        })
}

