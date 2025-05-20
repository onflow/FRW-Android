package com.flowfoundation.wallet.page.transaction.record

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.cache.transferRecordCache
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.TransferRecordList
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.transaction.record.model.TransactionViewMoreModel
import com.flowfoundation.wallet.page.transaction.toTransactionRecord
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.viewModelIOScope

private const val LIMIT = 30

class TransactionRecordViewModel : ViewModel(), OnTransactionStateChange {
    private var contractId: String? = null

    val transferCountLiveData = MutableLiveData<Int?>()

    val transferListLiveData = MutableLiveData<List<Any>>()

    init {
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    fun setContractId(contractId: String?) {
        this.contractId = contractId
    }

    override fun onTransactionStateChange() {
        load()
    }

    fun load() {
        viewModelIOScope(this) { loadTransfer() }
    }

    private suspend fun loadTransfer() {
        val wallet = WalletManager.wallet()
        val walletAddress = wallet?.walletAddress().orEmpty()
        logd("TransactionRecordViewModel", "Fetching transaction history. Wallet: $wallet, Wallet address: '$walletAddress'")
        if (walletAddress.isEmpty()) {
            logd("TransactionRecordViewModel", "Wallet address is empty, aborting transaction fetch.")
            return
        }
        if (WalletManager.isEVMAccountSelected()) {
            logd("TransactionRecordViewModel", "EVM account selected. Fetching EVM transfer records for address: '$walletAddress'")
            ioScope {
                try {
                    val service = retrofitApi().create(ApiService::class.java)
                    val resp = service.getEVMTransferRecord(walletAddress)
                    logd("TransactionRecordViewModel", "EVM transfer record response: $resp")
                    val data = mutableListOf<Any>().apply {
                        addAll(resp.trxs.orEmpty())
                    }
                    if ((resp.trxs?.size ?: 0) > LIMIT) {
                        data.add(TransactionViewMoreModel(walletAddress))
                    }
                    transferListLiveData.postValue(data)
                } catch (e: Exception) {
                    loge("TransactionRecordViewModel", "Error fetching EVM transfer records: ${e.message}")
                }
            }
        } else {
            logd("TransactionRecordViewModel", "Flow account selected. Fetching transfer records for address: '$walletAddress'")
            ioScope {
                try {
                    val service = retrofit().create(ApiService::class.java)
                    val resp = service.getTransferRecord(walletAddress, limit = LIMIT)
                    logd("TransactionRecordViewModel", "Transfer record response: $resp")
                    val processing = TransactionStateManager.getProcessingTransaction().map { it.toTransactionRecord() }
                    val transfers = resp.data?.transactions.orEmpty()
                    val data = mutableListOf<Any>().apply {
                        addAll(processing)
                        addAll(transfers)
                    }
                    if ((resp.data?.total ?: 0) > LIMIT) {
                        data.add(TransactionViewMoreModel(walletAddress))
                    }
                    transferListLiveData.postValue(data)
                } catch (e: Exception) {
                    loge("TransactionRecordViewModel", "Error fetching transfer records: ${e.message}")
                }
            }
        }
    }

    private fun isQueryByToken() = contractId != null
}