package com.flowfoundation.wallet.page.token.detail.model

import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import java.math.BigDecimal

data class MoveToken(
    val tokenBalance: BigDecimal,
    val tokenInfo: FlowCoin,
    val dollarValue: BigDecimal? = null,
) {

    fun getTokenId(fromAddress: String): String {
        return if (EVMWalletManager.isEVMWalletAddress(fromAddress)) {
            tokenInfo.evmAddress ?: tokenInfo.address
        } else {
            tokenInfo.flowIdentifier ?: tokenInfo.getFTIdentifier()
        }
    }
}
