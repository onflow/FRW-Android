package com.flowfoundation.wallet.page.backup.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.flowjvm.Cadence
import com.flowfoundation.wallet.manager.flowjvm.transactionByMainWallet
import com.flowfoundation.wallet.manager.flowjvm.ufix64Safe
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
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.nftco.flow.sdk.FlowTransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wallet.core.jni.HDWallet


class BackupSeedPhraseViewModel: ViewModel(), OnTransactionStateChange {

    val optionChangeLiveData = MutableLiveData<BackupSeedPhraseOption>()

    val createBackupCallbackLiveData = MutableLiveData<Boolean>()

    val mnemonicListLiveData = MutableLiveData<List<MnemonicModel>>()

    private val cryptoProvider: HDWalletCryptoProvider = HDWalletCryptoProvider(HDWallet(128, ""))

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
            cryptoProvider.let {
                try {
                    val txId = Cadence.CADENCE_ADD_PUBLIC_KEY.transactionByMainWallet {
                        arg { string(it.getPublicKey()) }
                        arg { uint8(it.getSignatureAlgorithm().index) }
                        arg { uint8(it.getHashAlgorithm().index) }
                        arg { ufix64Safe(1000) }
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
            cryptoProvider.let {
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
                                name = BackupType.FULL_WEIGHT_SEED_PHRASE.displayName,
                                type = BackupType.FULL_WEIGHT_SEED_PHRASE.index
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