package com.flowfoundation.wallet.page.transaction.record

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.page.transaction.record.model.TransactionViewMoreModel
import com.flowfoundation.wallet.page.transaction.toTransactionRecord
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.viewModelIOScope
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.network.retrofitApi

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

    private fun loadTransfer() {
        logd("TransactionRecordViewModel", "Starting loadTransfer(), checking wallet status")
        // Check if WalletManager is initialized
        if (WalletManager.wallet() == null) {
            logd("TransactionRecordViewModel", "WalletManager.wallet() is null, attempting to initialize")
            try {
                // Try to initialize WalletManager if not already initialized
                WalletManager.init()
                logd("TransactionRecordViewModel", "WalletManager initialized. New wallet status: ${WalletManager.wallet() != null}")
            } catch (e: Exception) {
                loge("TransactionRecordViewModel", "Failed to initialize WalletManager: ${e.message}")
                loge("TransactionRecordViewModel", "Error stacktrace: ${e.stackTraceToString()}")
            }
        }
        
        // Get the selected wallet address first
        val walletAddress = WalletManager.selectedWalletAddress()
        logd("TransactionRecordViewModel", "Selected wallet address: '$walletAddress'")
        
        // If we still don't have a wallet address, we can't proceed
        if (walletAddress.isEmpty()) {
            logd("TransactionRecordViewModel", "Could not find a valid wallet address from any source, aborting transaction fetch.")
            return
        }
        
        // Process with the wallet address we found
        logd("TransactionRecordViewModel", "Final wallet address: '$walletAddress'")
        fetchTransactions(walletAddress)
    }
    
    private fun fetchTransactions(walletAddress: String) {
        // Ensure address has 0x prefix
        val formattedAddress = if (walletAddress.startsWith("0x")) walletAddress else "0x$walletAddress"
        
        // Additional debugging
        logd("TransactionRecordViewModel", "=== TRANSACTION FETCH DEBUG ===")
        logd("TransactionRecordViewModel", "Input walletAddress: '$walletAddress'")
        logd("TransactionRecordViewModel", "Formatted address: '$formattedAddress'")
        logd("TransactionRecordViewModel", "WalletManager.selectedWalletAddress(): '${WalletManager.selectedWalletAddress()}'")
        logd("TransactionRecordViewModel", "WalletManager.isEVMAccountSelected(): ${WalletManager.isEVMAccountSelected()}")
        logd("TransactionRecordViewModel", "WalletManager.isChildAccountSelected(): ${WalletManager.isChildAccountSelected()}")
        logd("TransactionRecordViewModel", "WalletManager.wallet()?.walletAddress(): '${WalletManager.wallet()?.walletAddress()}'")
        
        // Check if this is a child account
        if (WalletManager.isChildAccountSelected()) {
            val childAccount = WalletManager.childAccount(walletAddress)
            logd("TransactionRecordViewModel", "Child account details: address='${childAccount?.address}', name='${childAccount?.name}'")
        }
        logd("TransactionRecordViewModel", "=== END DEBUG ===")

        if (WalletManager.isEVMAccountSelected()) {
            logd("TransactionRecordViewModel", "EVM account selected. Fetching EVM transfer records for address: '$formattedAddress'")
            ioScope {
                try {
                    val service = retrofitApi().create(ApiService::class.java)
                    logd("TransactionRecordViewModel", "Making API call to get EVM transfer records...")
                    val resp = service.getEVMTransferRecord(formattedAddress)
                    logd("TransactionRecordViewModel", "EVM transfer record response received. Status: ${resp.status}, Transactions: ${resp.trxs?.size ?: 0}")
                    val data = mutableListOf<Any>().apply {
                        addAll(resp.trxs.orEmpty())
                    }
                    logd("TransactionRecordViewModel", "EVM transfer records processed: ${data.size} items")
                    if ((resp.trxs?.size ?: 0) > LIMIT) {
                        data.add(TransactionViewMoreModel(formattedAddress))
                        logd("TransactionRecordViewModel", "Added 'View More' option for EVM transfers")
                    }
                    logd("TransactionRecordViewModel", "Posting EVM transaction data to UI: ${data.size} items")
                    transferListLiveData.postValue(data)
                    
                    // Update count regardless of empty list
                    transferCountLiveData.postValue(data.size)
                } catch (e: Exception) {
                    loge("TransactionRecordViewModel", "Error fetching EVM transfer records: ${e.message}")
                    loge("TransactionRecordViewModel", "Error stacktrace: ${e.stackTraceToString()}")
                    
                    // Ensure the UI shows something even on error
                    transferListLiveData.postValue(emptyList())
                    transferCountLiveData.postValue(0)
                }
            }
        } else {
            // This handles both regular Flow accounts and child accounts
            val accountType = if (WalletManager.isChildAccountSelected()) "child" else "regular Flow"
            logd("TransactionRecordViewModel", "$accountType account selected. Fetching Flow transfer records for address: '$formattedAddress'")
            logd("TransactionRecordViewModel", "About to call API with address: '$formattedAddress'")
            ioScope {
                try {
                    logd("TransactionRecordViewModel", "Creating API service for Flow transfers")
                    val service = retrofitApi().create(ApiService::class.java)
                    logd("TransactionRecordViewModel", "Making API call to get Flow transfer records for address: '$formattedAddress'")
                    val resp = service.getTransferRecord(formattedAddress, limit = LIMIT)
                    logd("TransactionRecordViewModel", "Transfer record response received. Status: ${resp.status}, Message: ${resp.message}")
                    logd("TransactionRecordViewModel", "Response data: Total=${resp.data?.total}, Next=${resp.data?.next}, Transactions=${resp.data?.transactions?.size ?: 0}")
                    
                    val processing = TransactionStateManager.getProcessingTransaction().map { it.toTransactionRecord() }
                    logd("TransactionRecordViewModel", "Processing transactions: ${processing.size}")
                    
                    val transfers = resp.data?.transactions.orEmpty()
                    logd("TransactionRecordViewModel", "Completed transfers: ${transfers.size}")
                    
                    val data = mutableListOf<Any>().apply {
                        if (processing.isNotEmpty()) {
                            logd("TransactionRecordViewModel", "Adding ${processing.size} processing transactions")
                            addAll(processing)
                        }
                        if (transfers.isNotEmpty()) {
                            logd("TransactionRecordViewModel", "Adding ${transfers.size} completed transactions")
                            addAll(transfers)
                        }
                    }
                    
                    if ((resp.data?.total ?: 0) > LIMIT) {
                        data.add(TransactionViewMoreModel(formattedAddress))
                        logd("TransactionRecordViewModel", "Added 'View More' option for Flow transfers")
                    }
                    
                    logd("TransactionRecordViewModel", "Posting Flow transaction data to UI: ${data.size} items")
                    transferListLiveData.postValue(data)
                    
                    // Also update the count
                    transferCountLiveData.postValue(data.size)
                } catch (e: Exception) {
                    loge("TransactionRecordViewModel", "Error fetching Flow transfer records: ${e.message}")
                    loge("TransactionRecordViewModel", "Error stacktrace: ${e.stackTraceToString()}")
                    
                    // Ensure the UI shows something even on error
                    transferListLiveData.postValue(emptyList())
                    transferCountLiveData.postValue(0)
                }
            }
        }
    }

}