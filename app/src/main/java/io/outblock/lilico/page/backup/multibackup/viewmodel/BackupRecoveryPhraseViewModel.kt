package io.outblock.lilico.page.backup.multibackup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nftco.flow.sdk.FlowTransactionStatus
import io.outblock.lilico.R
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.manager.backup.BackupCryptoProvider
import io.outblock.lilico.manager.flowjvm.CADENCE_ADD_PUBLIC_KEY
import io.outblock.lilico.manager.flowjvm.transactionByMainWallet
import io.outblock.lilico.manager.flowjvm.ufix64Safe
import io.outblock.lilico.manager.transaction.OnTransactionStateChange
import io.outblock.lilico.manager.transaction.TransactionState
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.model.AccountKey
import io.outblock.lilico.network.model.AccountSyncRequest
import io.outblock.lilico.network.model.BackupInfoRequest
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.backup.model.BackupType
import io.outblock.lilico.page.walletcreate.fragments.mnemonic.MnemonicModel
import io.outblock.lilico.page.window.bubble.tools.pushBubbleStack
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.textToClipboard
import io.outblock.lilico.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wallet.core.jni.HDWallet


class BackupRecoveryPhraseViewModel : ViewModel(), OnTransactionStateChange {
    val createBackupCallbackLiveData = MutableLiveData<Boolean>()

    val mnemonicListLiveData = MutableLiveData<List<MnemonicModel>>()

    private val backupCryptoProvider: BackupCryptoProvider = BackupCryptoProvider(HDWallet(160, ""))

    private var currentTxId: String? = null

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
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
                    val txId = CADENCE_ADD_PUBLIC_KEY.transactionByMainWallet {
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
                    createBackupCallbackLiveData.postValue(resp.status == 200)
                } catch (e: Exception) {
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
            if (currentTxId == state.transactionId && state.isSuccess()) {
                currentTxId = null
                syncKeyInfo()
            }
        }
    }
}