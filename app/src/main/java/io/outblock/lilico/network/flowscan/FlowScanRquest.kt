package io.outblock.lilico.network.flowscan

import com.google.gson.Gson
import io.outblock.lilico.manager.coin.FlowCoin
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.flowscan.model.FlowScanAccountTransferCountModel
import io.outblock.lilico.network.flowscan.model.FlowScanAccountTransferModel
import io.outblock.lilico.network.flowscan.model.FlowScanTokenTransferModel
import io.outblock.lilico.network.model.FlowScanQueryRequest
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.transaction.record.model.TransactionRecord
import io.outblock.lilico.wallet.removeAddressPrefix

suspend fun flowScanTokenTransferQuery(coin: FlowCoin): List<TransactionRecord>? {
    return flowScanTokenTransferQuery(coin.contractId())
}

suspend fun flowScanTokenTransferQuery(contractId: String): List<TransactionRecord>? {
    val address = WalletManager.selectedWalletAddress()
    val response = request(flowScanTokenTransferScript(address, contractId))
    val data = Gson().fromJson(response, FlowScanTokenTransferModel::class.java)

    return data.data?.data?.account?.tokenTransfers?.edges?.mapNotNull {
        val transaction = it?.node?.transaction
        if (transaction?.hash == null || transaction.time == null) null else TransactionRecord(transaction = transaction)
    }
}

suspend fun flowScanAccountTransferQuery(): List<TransactionRecord>? {
    val address = WalletManager.selectedWalletAddress()
    if (address.isEmpty()) {
        return null
    }
    val response = request(flowScanAccountTransferScript(address))
    val data = Gson().fromJson(response, FlowScanAccountTransferModel::class.java)

    return data.data?.data?.account?.transactions?.edges?.mapNotNull {
        val transaction = it?.transaction
        if (transaction?.hash == null || transaction.time == null) null else TransactionRecord(transaction = transaction)
    }
}

suspend fun flowScanAccountTransferCountQuery(): Int {
    val address = WalletManager.selectedWalletAddress()
    if (address.isEmpty()) {
        return 0
    }
    val response = request(flowScanAccountTransferCountScript(address))
    val data = Gson().fromJson(response, FlowScanAccountTransferCountModel::class.java)
    return data.data?.data?.account?.transactionCount ?: 0
}

private suspend fun request(script: String): String {
    val service = retrofit(true).create(ApiService::class.java)
    return service.flowScanQuery(Gson().toJson(FlowScanQueryRequest(query = script)))
}

fun FlowCoin.contractId() = "A.${address().removeAddressPrefix()}.${contractName}"