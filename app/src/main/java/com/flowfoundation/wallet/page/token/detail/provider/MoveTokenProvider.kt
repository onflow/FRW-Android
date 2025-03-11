package com.flowfoundation.wallet.page.token.detail.provider

import com.flowfoundation.wallet.page.token.detail.model.MoveToken


interface MoveTokenProvider {
    suspend fun getMoveTokenList(walletAddress: String): List<MoveToken>
    suspend fun getMoveToken(tokenId: String, walletAddress: String): MoveToken?
    suspend fun getMoveToken(contractId: String): MoveToken?
    fun getTokenListUrl(): String
    fun getFileName(): String
}