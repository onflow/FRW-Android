package com.flowfoundation.wallet.page.backup.multibackup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.onflow.flow.models.TransactionStatus
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
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
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupRecoveryPhraseOption
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.error.BackupError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage
import com.flowfoundation.wallet.utils.Env
import java.io.File
import wallet.core.jni.HDWallet
import com.flowfoundation.wallet.utils.logd
import org.onflow.flow.infrastructure.Cadence.Companion.uint8

class BackupRecoveryPhraseViewModel : ViewModel(), OnTransactionStateChange {
    val createBackupCallbackLiveData = MutableLiveData<Boolean>()

    val mnemonicListLiveData = MutableLiveData<List<MnemonicModel>>()

    val optionChangeLiveData = MutableLiveData<BackupRecoveryPhraseOption>()

    private val backupCryptoProvider: BackupCryptoProvider = run {
        val baseDir = File(Env.getApp().filesDir, "wallet")
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = HDWallet(160, "").mnemonic(),
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = FileSystemStorage(baseDir)
        )
        BackupCryptoProvider(seedPhraseKey)
    }

    private var currentTxId: String? = null

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    fun changeOption(option: BackupRecoveryPhraseOption) {
        optionChangeLiveData.postValue(option)
    }

    fun getMnemonic(): String {
        return backupCryptoProvider.getMnemonic()
    }

    fun loadMnemonic() {
        ioScope {
            val str = backupCryptoProvider.getMnemonic()
            withContext(Dispatchers.Main) {
                val list = str.split(" ").mapIndexed { index, s -> MnemonicModel(index + 1, s) }
                val result = mutableListOf<MnemonicModel>()
                val mid = list.size / 2 + 1
                (0 until mid).forEach { i ->
                    result.add(list[i])
                    val j = i + mid
                    if (j < list.size) {
                        result.add(list[j])
                    }
                }
                mnemonicListLiveData.value = result
            }
        }
    }

    fun copyMnemonic() {
        textToClipboard(backupCryptoProvider.getMnemonic())
        toast(R.string.copied_to_clipboard)
    }

    fun uploadToChainAndSync() {
        ioScope {
            backupCryptoProvider.let {
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
                    throw e
                }
            }
        }
    }

    private fun syncKeyInfo() {
        ioScope {
            backupCryptoProvider.let {
                try {
                    logd("BackupRecoveryPhrase", "Starting syncKeyInfo for Manual backup")
                    val deviceInfo = DeviceInfoManager.getDeviceInfoRequest()
                    val service = retrofit().create(ApiService::class.java)
                    val publicKey = it.getPublicKey()
                    logd("BackupRecoveryPhrase", "Public key for sync: $publicKey")
                    logd("BackupRecoveryPhrase", "Public key length: ${publicKey.length}")
                    
                    // Ensure the public key is in the correct format for the API (64 bytes, no 04 prefix)
                    val normalizedPublicKey = publicKey.removePrefix("0x").removePrefix("04")
                    logd("BackupRecoveryPhrase", "Normalized public key for sync: $normalizedPublicKey")
                    logd("BackupRecoveryPhrase", "Normalized public key length: ${normalizedPublicKey.length}")
                    
                    val resp = service.syncAccount(
                        AccountSyncRequest(
                            AccountKey(
                                publicKey = normalizedPublicKey,
                                signAlgo = it.getSignatureAlgorithm().cadenceIndex,
                                hashAlgo = it.getHashAlgorithm().cadenceIndex,
                                weight = it.getKeyWeight()
                            ),
                            deviceInfo,
                            BackupInfoRequest(
                                name = BackupType.MANUAL.displayName,
                                type = BackupType.MANUAL.index
                            )
                        )
                    )
                    logd("BackupRecoveryPhrase", "Sync response status: ${resp.status}")
                    logd("BackupRecoveryPhrase", "Sync response: $resp")
                    MixpanelManager.multiBackupCreated(MixpanelBackupProvider.SEED_PHRASE)
                    createBackupCallbackLiveData.postValue(resp.status == 200)
                } catch (e: Exception) {
                    android.util.Log.e("BackupRecoveryPhrase", "Sync failed", e)
                    ErrorReporter.reportWithMixpanel(BackupError.SYNC_ACCOUNT_INFO_FAILED, e)
                    MixpanelManager.multiBackupCreationFailed(MixpanelBackupProvider.SEED_PHRASE)
                    createBackupCallbackLiveData.postValue(false)
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
                    currentTxId = null
                    syncKeyInfo()
                } else if (state.isFailed()) {
                    MixpanelManager.multiBackupCreationFailed(MixpanelBackupProvider.SEED_PHRASE)
                }
            }
        }
    }

}