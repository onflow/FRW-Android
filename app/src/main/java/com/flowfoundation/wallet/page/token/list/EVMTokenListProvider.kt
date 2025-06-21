package com.flowfoundation.wallet.page.token.list

import com.flowfoundation.wallet.manager.coin.CustomTokenManager
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.toFungibleToken
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency
import com.flowfoundation.wallet.page.token.custom.model.TokenType
import com.flowfoundation.wallet.page.token.custom.model.toFungibleToken
import com.flowfoundation.wallet.utils.ioScope


class EVMTokenListProvider(private val walletAddress: String): TokenListProvider {

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
        val tokenResponse = service.getEVMTokenList(walletAddress, currency?.name, network)
        tokenList.clear()
        tokenList.addAll(
            tokenResponse.data?.map { token ->
                token.toFungibleToken()
            }?.toList() ?: emptyList()
        )
        addCustomToken()
        return tokenList
    }

    override fun addCustomToken() {
        val customTokenItems = CustomTokenManager.getCurrentCustomTokenList()
        val newFungibleTokens = customTokenItems.mapNotNull { customItem ->
            if (customItem.tokenType == TokenType.EVM) {
                customItem.toFungibleToken()
            } else {
                null
            }
        }.filter { ft ->
            tokenList.none { existingToken ->
                existingToken.evmAddress?.equals(ft.evmAddress, ignoreCase = true) == true
            }
        }
        tokenList.addAll(newFungibleTokens)
    }

    override fun deleteCustomToken(contractAddress: String) {
        tokenList.removeIf { it.evmAddress?.equals(contractAddress, true) == true }
    }

    override fun getWalletAddress(): String {
        return walletAddress
    }

    override fun getFungibleTokenListSnapshot(): List<FungibleToken> {
        return tokenList
    }

    override fun getTokenById(contractId: String): FungibleToken? {
        return tokenList.firstOrNull { it.contractId() == contractId }
    }

    override fun getFlowToken(): FungibleToken? {
        return tokenList.firstOrNull { it.isFlowToken() }
    }
}