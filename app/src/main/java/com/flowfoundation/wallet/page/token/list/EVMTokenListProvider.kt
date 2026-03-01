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
        
        val newTokens = tokenResponse.data?.map { token ->
            token.toFungibleToken()
        }?.toMutableList() ?: mutableListOf()
        
        val customTokens = getCustomTokens()
        val uniqueCustomTokens = customTokens.filter { ft ->
            newTokens.none { existingToken ->
                existingToken.evmAddress?.equals(ft.evmAddress, ignoreCase = true) == true
            }
        }
        newTokens.addAll(uniqueCustomTokens)

        synchronized(this) {
            tokenList.clear()
            tokenList.addAll(newTokens)
        }
        return newTokens
    }

    private fun getCustomTokens(): List<FungibleToken> {
        val customTokenItems = CustomTokenManager.getCurrentCustomTokenList()
        return customTokenItems.mapNotNull { customItem ->
            if (customItem.tokenType == TokenType.EVM) {
                customItem.toFungibleToken()
            } else {
                null
            }
        }
    }

    override fun addCustomToken() {
        synchronized(this) {
            val customTokens = getCustomTokens()
            val newFungibleTokens = customTokens.filter { ft ->
                tokenList.none { existingToken ->
                    existingToken.evmAddress?.equals(ft.evmAddress, ignoreCase = true) == true
                }
            }
            tokenList.addAll(newFungibleTokens)
        }
    }

    override fun deleteCustomToken(contractAddress: String) {
        tokenList.removeIf { it.evmAddress?.equals(contractAddress, true) == true }
    }

    override fun getWalletAddress(): String {
        return walletAddress
    }

    override fun getFungibleTokenListSnapshot(): List<FungibleToken> {
        return synchronized(this) { tokenList.toList() }
    }

    override fun getTokenById(contractId: String): FungibleToken? {
        return synchronized(this) { tokenList.firstOrNull { it.contractId() == contractId } }
    }

    override fun getFlowToken(): FungibleToken? {
        return synchronized(this) { tokenList.firstOrNull { it.isFlowToken() } }
    }
}