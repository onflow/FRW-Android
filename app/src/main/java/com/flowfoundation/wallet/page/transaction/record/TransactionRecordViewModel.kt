package com.flowfoundation.wallet.page.transaction.record

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.cache.transferRecordCache
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.TransferRecordList
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.transaction.record.model.TransactionViewMoreModel
import com.flowfoundation.wallet.page.transaction.toTransactionRecord
import com.flowfoundation.wallet.utils.updateAccountTransferCount
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
        transferListLiveData.postValue(transferRecordCache(contractId.orEmpty()).read()?.list.orEmpty())
//        transferCountLiveData.postValue(getAccountTransferCount())

        val service = retrofit().create(ApiService::class.java)
        val walletAddress = WalletManager.selectedWalletAddress()
        if (walletAddress.isEmpty()) {
            return
        }
        val resp = if (isQueryByToken()) {
            service.getTransferRecordByToken(walletAddress, contractId!!, limit = LIMIT)
        } else {
            service.getTransferRecord(walletAddress, limit = LIMIT)
        }
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
//        transferCountLiveData.postValue((resp.data?.total ?: 0) + processing.size)

        transferRecordCache(contractId.orEmpty()).cache(TransferRecordList(transfers))
        updateAccountTransferCount(resp.data?.total ?: 0)
    }

    private fun isQueryByToken() = contractId != null
}