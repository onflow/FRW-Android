package com.flowfoundation.wallet.page.token.list

import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.toFungibleToken
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency
import com.flowfoundation.wallet.utils.ioScope


class CadenceTokenListProvider(private val walletAddress: String): TokenListProvider {
    private var tokenList = mutableListOf<FungibleToken>()
    private val service by lazy { retrofitApi().create(ApiService::class.java)  }

    init {
        ioScope {
            getTokenList(walletAddress)
        }
    }

    override suspend fun getTokenList(
        walletAddress: String,
        currency: Currency?,
        network: String?
    ): List<FungibleToken> {
        val tokenResponse = service.getFlowTokenList(walletAddress, currency?.name, network)
        tokenList.clear()
        tokenList.addAll(
            tokenResponse.data?.result?.map { token ->
                token.toFungibleToken()
            }?.toList() ?: emptyList()
        )
        return tokenList
    }

    override fun getTokenById(contractId: String): FungibleToken? {
        return tokenList.firstOrNull { it.contractId() == contractId }
    }

    override fun getFlowToken(): FungibleToken? {
        return tokenList.firstOrNull { it.isFlowToken() }
    }

    override fun addCustomToken() {

    }

    override fun deleteCustomToken(contractAddress: String) {

    }

    override fun getWalletAddress(): String {
        return walletAddress
    }

    override fun getFungibleTokenListSnapshot(): List<FungibleToken> {
        return tokenList
    }

} 