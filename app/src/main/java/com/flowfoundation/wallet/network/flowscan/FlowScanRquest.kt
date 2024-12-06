package com.flowfoundation.wallet.network.flowscan

import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofit

suspend fun flowScanAccountTransferCountQuery(): Int {
    val address = WalletManager.selectedWalletAddress()
    if (address.isEmpty()) {
        return 0
    }
    val service = retrofit().create(ApiService::class.java)
    val response = service.flowScanQuery(address)
    return response.data?.data?.participationAggregate?.aggregate?.count ?: 0
}
