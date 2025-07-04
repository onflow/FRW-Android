package com.flowfoundation.wallet.page.backup.multibackup.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.onflow.flow.models.TransactionStatus
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.manager.drive.ACTION_GOOGLE_DRIVE_LOGIN_FINISH
import com.flowfoundation.wallet.manager.backup.ACTION_GOOGLE_DRIVE_UPLOAD_FINISH
import com.flowfoundation.wallet.manager.backup.EXTRA_SUCCESS
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.transactionByMainWallet
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
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupGoogleDriveState
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.error.BackupError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.ioScope
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage
import java.io.File
import wallet.core.jni.HDWallet
import com.flow.wallet.wallet.KeyWallet
import com.flow.wallet.wallet.WalletFactory
import com.flowfoundation.wallet.utils.Env.getStorage
import org.onflow.flow.ChainId
import org.onflow.flow.infrastructure.Cadence.Companion.uint8


class BackupGoogleDriveViewModel : ViewModel(), OnTransactionStateChange {

    val backupStateLiveData = MutableLiveData<BackupGoogleDriveState>()
    val uploadMnemonicLiveData = MutableLiveData<String>()
    private var backupCryptoProvider: BackupCryptoProvider? = null
    private var currentTxId: String? = null

    private val uploadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val isSuccess = intent?.getBooleanExtra(EXTRA_SUCCESS, false) ?: return
            backupStateLiveData.postValue(
                if (isSuccess) {
                    BackupGoogleDriveState.REGISTRATION_KEY_LIST
                } else {
                    BackupGoogleDriveState.UPLOAD_BACKUP_FAILURE
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
                backupStateLiveData.postValue(BackupGoogleDriveState.CREATE_BACKUP)
            }
        }
    }

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
        LocalBroadcastManager.getInstance(Env.getApp()).registerReceiver(
            uploadReceiver, IntentFilter(
                ACTION_GOOGLE_DRIVE_UPLOAD_FINISH
            )
        )
        LocalBroadcastManager.getInstance(Env.getApp()).registerReceiver(
            loginFinishReceiver, IntentFilter(
                ACTION_GOOGLE_DRIVE_LOGIN_FINISH
            )
        )
    }

    fun createBackup() {
        val baseDir = File(Env.getApp().filesDir, "wallet")
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = HDWallet(160, "").mnemonic(),
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = FileSystemStorage(baseDir)
        )
        createBackupCryptoProvider(seedPhraseKey)
        backupStateLiveData.postValue(BackupGoogleDriveState.UPLOAD_BACKUP)
    }

    private fun createBackupCryptoProvider(seedPhraseKey: SeedPhraseKey) {
        val wallet = WalletFactory.createKeyWallet(
            seedPhraseKey,
            setOf(ChainId.Mainnet, ChainId.Testnet),
            getStorage()
        )
        backupCryptoProvider = BackupCryptoProvider(seedPhraseKey, wallet as KeyWallet)
    }

    fun uploadToChain() {
        ioScope {
            backupCryptoProvider?.let {
                try {
                    val txId = CadenceScript.CADENCE_ADD_PUBLIC_KEY.transactionByMainWallet {
                        val pubKeyWithPrefix = it.getPublicKey() // e.g., "04..."
                        val pubKeyHexRaw = pubKeyWithPrefix.removePrefix("0x")
                        
                        // Flow's Cadence addKey script expects the publicKey string argument to be the
                        // 64-byte hex representation (128 chars) WITHOUT the "04" uncompressed prefix.
                        val pubKeyForCadence = if (pubKeyHexRaw.startsWith("04") && pubKeyHexRaw.length == 130) {
                            pubKeyHexRaw.substring(2)
                        } else {
                            pubKeyHexRaw
                        }
                        
                        arg { string(pubKeyForCadence) }
                        arg { uint8(it.getSignatureAlgorithm().cadenceIndex.toUByte()) }
                        arg { uint8(it.getHashAlgorithm().cadenceIndex.toUByte()) }
                        arg { ufix64Safe(500) }
                    }
                    val transactionState = TransactionState(
                        transactionId = txId!!,
                        time = System.currentTimeMillis(),
                        state = TransactionStatus.PENDING.ordinal,
                        type = TransactionState.TYPE_ADD_PUBLIC_KEY,
                        data = ""
                    )
                    currentTxId = txId
                    TransactionStateManager.newTransaction(transactionState)
                    pushBubbleStack(transactionState)
                } catch (e: Exception) {
                    ErrorReporter.reportWithMixpanel(BackupError.ADD_PUBLIC_KEY_FAILED, e)
                    MixpanelManager.multiBackupCreationFailed(MixpanelBackupProvider.GOOGLE_DRIVE)
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
                                name = BackupType.GOOGLE_DRIVE.displayName,
                                type = BackupType.GOOGLE_DRIVE.index
                            )
                        )
                    )
                    if (resp.status == 200) {
                        MixpanelManager.multiBackupCreated(MixpanelBackupProvider.GOOGLE_DRIVE)
                        backupStateLiveData.postValue(BackupGoogleDriveState.BACKUP_SUCCESS)
                    } else {
                        backupStateLiveData.postValue(BackupGoogleDriveState.NETWORK_ERROR)
                    }
                } catch (e: Exception) {
                    ErrorReporter.reportWithMixpanel(BackupError.SYNC_ACCOUNT_INFO_FAILED, e)
                    MixpanelManager.multiBackupCreationFailed(MixpanelBackupProvider.GOOGLE_DRIVE)
                    backupStateLiveData.postValue(BackupGoogleDriveState.NETWORK_ERROR)
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
                    MixpanelManager.multiBackupCreationFailed(MixpanelBackupProvider.GOOGLE_DRIVE)
                }
            }
        }
    }

}