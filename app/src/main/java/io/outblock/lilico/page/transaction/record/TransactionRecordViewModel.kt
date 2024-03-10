package io.outblock.lilico.page.transaction.record

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.outblock.lilico.cache.transferRecordCache
import io.outblock.lilico.manager.transaction.OnTransactionStateChange
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.model.TransferRecordList
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.transaction.record.model.TransactionViewMoreModel
import io.outblock.lilico.page.transaction.toTransactionRecord
import io.outblock.lilico.utils.getAccountTransferCount
import io.outblock.lilico.utils.updateAccountTransferCount
import io.outblock.lilico.utils.viewModelIOScope

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