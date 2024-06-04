package com.flowfoundation.wallet.network.flowscan

import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.wallet.removeAddressPrefix

suspend fun flowScanAccountTransferCountQuery(): Int {
    val address = WalletManager.selectedWalletAddress()
    if (address.isEmpty()) {
        return 0
    }
    val service = retrofit().create(ApiService::class.java)
    val response = service.flowScanQuery(address)
    return response.data?.data?.participationAggregate?.aggregate?.count ?: 0
}

fun FlowCoin.contractId() = "A.${address.removeAddressPrefix()}.${contractName()}"