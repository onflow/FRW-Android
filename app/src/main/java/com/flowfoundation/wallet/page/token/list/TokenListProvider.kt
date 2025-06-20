package com.flowfoundation.wallet.page.token.list

import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency


interface TokenListProvider {
    suspend fun getTokenList(walletAddress: String, currency: Currency? = selectedCurrency(), network: String? = chainNetWorkString()): List<FungibleToken>
    fun getTokenById(contractId: String): FungibleToken?
    fun getFlowToken(): FungibleToken?
    fun getFlowTokenContractId() = getFlowToken()?.contractId().orEmpty()
    fun addCustomToken()
    fun deleteCustomToken(contractAddress: String)
    fun getWalletAddress(): String
    fun getFungibleTokenListSnapshot(): List<FungibleToken>
} 