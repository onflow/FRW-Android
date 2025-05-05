package com.flowfoundation.wallet.page.backup.multibackup.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.manager.dropbox.EXTRA_SUCCESS
import com.flowfoundation.wallet.manager.dropbox.ACTION_DROPBOX_LOGIN_FINISH
import com.flowfoundation.wallet.manager.dropbox.ACTION_DROPBOX_UPLOAD_FINISH
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.transactionByMainWallet
import com.flowfoundation.wallet.manager.flowjvm.ufix64Safe
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.mixpanel.MixpanelBackupProvider
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.AccountSyncRequest
import com.flowfoundation.wallet.network.model.BackupInfoRequest
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupDropboxState
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.error.BackupError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.ioScope
import wallet.core.jni.HDWallet


class BackupDropboxViewModel : ViewModel(), OnTransactionStateChange {

    val backupStateLiveData = MutableLiveData<BackupDropboxState>()
    val uploadMnemonicLiveData = MutableLiveData<String>()
    private var backupCryptoProvider: BackupCryptoProvider? = null
    private var currentTxId: String? = null

    private val uploadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val isSuccess = intent?.getBooleanExtra(EXTRA_SUCCESS, false) ?: return
            backupStateLiveData.postValue(
                if (isSuccess) {
                    BackupDropboxState.REGISTRATION_KEY_LIST
                } else {
                    BackupDropboxState.UPLOAD_BACKUP_FAILURE
                }
            )
            if (isSuccess) {
                registrationKeyList()
            }
        }
    }

    private val loginFinishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isSuccess = intent?.getBooleanExtra(EXTRA_SUCCESS, false) ?: false
            if (isSuccess) {
                createBackup()
            } else {
                backupStateLiveData.postValue(BackupDropboxState.CREATE_BACKUP)
            }
        }
    }

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
        LocalBroadcastManager.getInstance(Env.getApp()).registerReceiver(
            uploadReceiver, IntentFilter(
                ACTION_DROPBOX_UPLOAD_FINISH
            )
        )
        LocalBroadcastManager.getInstance(Env.getApp()).registerReceiver(
            loginFinishReceiver, IntentFilter(
                ACTION_DROPBOX_LOGIN_FINISH
            )
        )
    }

    fun createBackup() {
        backupCryptoProvider = BackupCryptoProvider(HDWallet(160, ""))
        backupStateLiveData.postValue(BackupDropboxState.UPLOAD_BACKUP)
    }

    fun uploadToChain() {
        ioScope {
            backupCryptoProvider?.let {
                try {
                    val txId = CadenceScript.CADENCE_ADD_PUBLIC_KEY.transactionByMainWallet {
                        arg { string(it.getPublicKey()) }
                        arg { uint8(it.getSignatureAlgorithm().cadenceIndex) }
                        arg { uint8(it.getHashAlgorithm().cadenceIndex) }
                        arg { ufix64Safe(500) }
                    }
                    val transactionState = TransactionState(
                        transactionId = txId!!,
                        time = System.currentTimeMillis(),
                        state = FlowTransactionStatus.PENDING.num,
                        type = TransactionState.TYPE_ADD_PUBLIC_KEY,
                        data = ""
                    )
                    currentTxId = txId
                    TransactionStateManager.newTransaction(transactionState)
                    pushBubbleStack(transactionState)
                } catch (e: Exception) {
                    ErrorReporter.reportWithMixpanel(BackupError.ADD_PUBLIC_KEY_FAILED, e)
                    MixpanelManager.multiBackupCreationFailed(MixpanelBackupProvider.DROPBOX)
                    throw e
                }
            }
        }
    }

    override fun onCleared() {
        LocalBroadcastManager.getInstance(Env.getApp()).unregisterReceiver(uploadReceiver)
        LocalBroadcastManager.getInstance(Env.getApp()).unregisterReceiver(loginFinishReceiver)
        super.onCleared()
    }

    fun getMnemonic(): String {
        return backupCryptoProvider?.getMnemonic() ?: ""
    }

    fun registrationKeyList() {
        ioScope {
            backupCryptoProvider?.let {
                try {
                    val deviceInfo = DeviceInfoManager.getDeviceInfoRequest()
                    val service = retrofit().create(ApiService::class.java)
                    val resp = service.syncAccount(
                        AccountSyncRequest(
                            AccountKey(
                                publicKey = it.getPublicKey(),
                                signAlgo = it.getSignatureAlgorithm().cadenceIndex,
                                hashAlgo = it.getHashAlgorithm().cadenceIndex,
                                weight = it.getKeyWeight()
                            ),
                            deviceInfo,
                            BackupInfoRequest(
                                name = BackupType.DROPBOX.displayName,
                                type = BackupType.DROPBOX.index
                            )
                        )
                    )
                    if (resp.status == 200) {
                        MixpanelManager.multiBackupCreated(MixpanelBackupProvider.DROPBOX)
                        backupStateLiveData.postValue(BackupDropboxState.BACKUP_SUCCESS)
                    } else {
                        backupStateLiveData.postValue(BackupDropboxState.NETWORK_ERROR)
                    }
                } catch (e: Exception) {
                    ErrorReporter.reportWithMixpanel(BackupError.SYNC_ACCOUNT_INFO_FAILED, e)
                    MixpanelManager.multiBackupCreationFailed(MixpanelBackupProvider.DROPBOX)
                    backupStateLiveData.postValue(BackupDropboxState.NETWORK_ERROR)
                }
            }
        }
    }

    override fun onTransactionStateChange() {
        val transactionList = TransactionStateManager.getTransactionStateList()
        val transaction =
            transactionList.lastOrNull { it.type == TransactionState.TYPE_ADD_PUBLIC_KEY }
        transaction?.let { state ->
            if (currentTxId == state.transactionId) {
                if (state.isSuccess()) {
                    val mnemonic = backupCryptoProvider?.getMnemonic() ?: throw RuntimeException("Mnemonic cannot be null")
                    uploadMnemonicLiveData.postValue(mnemonic)
                    currentTxId = null
                } else if (state.isFailed()) {
                    MixpanelManager.multiBackupCreationFailed(MixpanelBackupProvider.DROPBOX)
                }
            }
        }
    }

}