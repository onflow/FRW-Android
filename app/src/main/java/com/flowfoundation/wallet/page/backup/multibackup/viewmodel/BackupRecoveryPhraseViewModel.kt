package com.flowfoundation.wallet.page.backup.multibackup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.manager.flowjvm.Cadence
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
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupRecoveryPhraseOption
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicModel
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wallet.core.jni.HDWallet


class BackupRecoveryPhraseViewModel : ViewModel(), OnTransactionStateChange {
    val createBackupCallbackLiveData = MutableLiveData<Boolean>()

    val mnemonicListLiveData = MutableLiveData<List<MnemonicModel>>()

    val optionChangeLiveData = MutableLiveData<BackupRecoveryPhraseOption>()

    private val backupCryptoProvider: BackupCryptoProvider = BackupCryptoProvider(HDWallet(160, ""))

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
                    val txId = Cadence.CADENCE_ADD_PUBLIC_KEY.transactionByMainWallet {
                        arg { string(it.getPublicKey()) }
                        arg { uint8(it.getSignatureAlgorithm().index) }
                        arg { uint8(it.getHashAlgorithm().index) }
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
                    throw e
                }
            }
        }
    }

    private fun syncKeyInfo() {
        ioScope {
            backupCryptoProvider.let {
                try {
                    val deviceInfo = DeviceInfoManager.getDeviceInfoRequest()
                    val service = retrofit().create(ApiService::class.java)
                    val resp = service.syncAccount(
                        AccountSyncRequest(
                            AccountKey(
                                publicKey = it.getPublicKey(),
                                signAlgo = it.getSignatureAlgorithm().index,
                                hashAlgo = it.getHashAlgorithm().index,
                                weight = it.getKeyWeight()
                            ),
                            deviceInfo,
                            BackupInfoRequest(
                                name = BackupType.MANUAL.displayName,
                                type = BackupType.MANUAL.index
                            )
                        )
                    )
                    MixpanelManager.multiBackupCreated(MixpanelBackupProvider.SEED_PHRASE)
                    createBackupCallbackLiveData.postValue(resp.status == 200)
                } catch (e: Exception) {
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