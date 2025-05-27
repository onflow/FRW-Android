package com.flowfoundation.wallet.page.backup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.transactionByMainWallet
import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.AccountKey
import com.flowfoundation.wallet.network.model.AccountSyncRequest
import com.flowfoundation.wallet.network.model.BackupInfoRequest
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupSeedPhraseOption
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.error.BackupError
import com.flowfoundation.wallet.utils.error.ErrorReporter
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import org.onflow.flow.models.TransactionStatus
import com.flow.wallet.keys.SeedPhraseKey
import com.flow.wallet.storage.FileSystemStorage
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.logd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.onflow.flow.infrastructure.Cadence.Companion.uint8
import java.io.File
import wallet.core.jni.HDWallet

class BackupSeedPhraseViewModel: ViewModel(), OnTransactionStateChange {

    val optionChangeLiveData = MutableLiveData<BackupSeedPhraseOption>()

    val createBackupCallbackLiveData = MutableLiveData<Boolean>()

    val mnemonicListLiveData = MutableLiveData<List<MnemonicModel>>()

    private val cryptoProvider: HDWalletCryptoProvider = run {
        val baseDir = File(Env.getApp().filesDir, "wallet")
        val seedPhraseKey = SeedPhraseKey(
            mnemonicString = HDWallet(128, "").mnemonic(),
            passphrase = "",
            derivationPath = "m/44'/539'/0'/0/0",
            keyPair = null,
            storage = FileSystemStorage(baseDir)
        )
        HDWalletCryptoProvider(seedPhraseKey)
    }

    private var currentTxId: String? = null

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    fun changeOption(option: BackupSeedPhraseOption) {
        optionChangeLiveData.postValue(option)
    }

    fun getMnemonic(): String {
        return cryptoProvider.getMnemonic()
    }

    fun loadMnemonic() {
        ioScope {
            val str = cryptoProvider.getMnemonic()
            withContext(Dispatchers.Main) {
                val list = str.split(" ").mapIndexed { index, s -> MnemonicModel(index + 1, s) }
                val result = mutableListOf<MnemonicModel>()
                (0 until list.size / 2).forEach { i ->
                    result.add(list[i])
                    result.add(list[i + list.size / 2])
                }
                mnemonicListLiveData.value = result
            }
        }
    }

    fun copyMnemonic() {
        textToClipboard(cryptoProvider.getMnemonic())
        toast(R.string.copied_to_clipboard)
    }

    fun uploadToChain() {
        ioScope {
            try {
                withTimeout(45000) { // 45 second timeout (longer than Flow's 30s to catch timeouts)
                    cryptoProvider.let {
                        try {
                            val txId = CadenceScript.CADENCE_ADD_PUBLIC_KEY.transactionByMainWallet {
                                val newPubKeyWithPrefix = it.getPublicKey() // e.g., "0x04..."
                                val newPubKeyHexRaw = newPubKeyWithPrefix.removePrefix("0x")
                                
                                // Flow's Cadence addKey script expects the publicKey string argument to be the
                                // 64-byte hex representation (128 chars) WITHOUT the "04" uncompressed prefix.
                                val newPubKeyForCadence = if (newPubKeyHexRaw.startsWith("04") && newPubKeyHexRaw.length == 130) {
                                    newPubKeyHexRaw.substring(2)
                                } else {
                                    newPubKeyHexRaw
                                }
                                logd("BackupSeedPhraseVM", "Original new public key: $newPubKeyWithPrefix")
                                logd("BackupSeedPhraseVM", "Stripped public key for Cadence: $newPubKeyForCadence")

                                arg { string(newPubKeyForCadence) }
                                arg { uint8(it.getSignatureAlgorithm().cadenceIndex.toUByte()) }
                                arg { uint8(it.getHashAlgorithm().cadenceIndex.toUByte()) }
                                arg { ufix64Safe(1000) }
                            }
                            if (txId.isNullOrBlank()) {
                                // Handle case where transaction ID is null or empty
                                ErrorReporter.reportWithMixpanel(BackupError.ADD_PUBLIC_KEY_FAILED, 
                                    RuntimeException("Failed to get transaction ID"))
                                createBackupCallbackLiveData.postValue(false)
                                return@withTimeout
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
                            // Reset state and notify UI of failure
                            currentTxId = null
                            createBackupCallbackLiveData.postValue(false)
                            ErrorReporter.reportWithMixpanel(BackupError.ADD_PUBLIC_KEY_FAILED, e)
                            throw e
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // Handle timeout specifically
                currentTxId = null
                createBackupCallbackLiveData.postValue(false)
                ErrorReporter.reportWithMixpanel(BackupError.ADD_PUBLIC_KEY_FAILED, 
                    RuntimeException("Backup process timed out after 45 seconds"))
                toast(R.string.backup_failed)
            }
        }
    }

    private fun syncKeyInfo() {
        ioScope {
            try {
                withTimeout(30000) { // 30 second timeout for network requests
                    cryptoProvider.let {
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
                                        name = BackupType.FULL_WEIGHT_SEED_PHRASE.displayName,
                                        type = BackupType.FULL_WEIGHT_SEED_PHRASE.index
                                    )
                                )
                            )
                            val isSuccess = resp.status == 200
                            if (!isSuccess) {
                                // Log detailed error information for debugging
                                ErrorReporter.reportWithMixpanel(BackupError.SYNC_ACCOUNT_INFO_FAILED, 
                                    RuntimeException("Sync failed with status: ${resp.status}"))
                            }
                            createBackupCallbackLiveData.postValue(isSuccess)
                        } catch (e: Exception) {
                            ErrorReporter.reportWithMixpanel(BackupError.SYNC_ACCOUNT_INFO_FAILED, e)
                            createBackupCallbackLiveData.postValue(false)
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // Handle timeout for network sync
                ErrorReporter.reportWithMixpanel(BackupError.SYNC_ACCOUNT_INFO_FAILED, 
                    RuntimeException("Account sync timed out after 30 seconds"))
                createBackupCallbackLiveData.postValue(false)
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
                    // Handle failed transactions to prevent infinite loading
                    currentTxId = null
                    createBackupCallbackLiveData.postValue(false)
                    ErrorReporter.reportWithMixpanel(BackupError.ADD_PUBLIC_KEY_FAILED, 
                        RuntimeException("Transaction failed: ${state.errorMsg}"))
                }
            }
        }
    }
}