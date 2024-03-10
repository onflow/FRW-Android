package io.outblock.lilico.network.flowscan

import io.outblock.lilico.manager.coin.FlowCoin
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.wallet.removeAddressPrefix

suspend fun flowScanAccountTransferCountQuery(): Int {
    val address = WalletManager.selectedWalletAddress()
    if (address.isEmpty()) {
        return 0
    }
    val service = retrofit().create(ApiService::class.java)
    val response = service.flowScanQuery(address)
    return response.data?.data?.participationAggregate?.aggregate?.count ?: 0
}

fun FlowCoin.contractId() = "A.${address().removeAddressPrefix()}.${contractName}"