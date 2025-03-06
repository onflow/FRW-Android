package com.flowfoundation.wallet.page.token.detail.provider

import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.FlowCoinType
import com.flowfoundation.wallet.manager.coin.TokenList
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenBalanceWithAddress
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryTokenListBalanceWithAddress
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.token.detail.model.MoveToken
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isDev
import com.google.gson.Gson
import java.math.BigDecimal
import java.net.URL

class FlowAccountTokenProvider : MoveTokenProvider {
    private var coinList = mutableListOf<FlowCoin>()

    init {
        ioScope {
            fetchCoinList()
        }
    }

    override suspend fun getMoveTokenList(walletAddress: String): List<MoveToken> {
        val tokens = if (coinList.isEmpty()) {
            fetchCoinList()
        } else {
            coinList
        }
        return generationMoveTokenList(tokens, walletAddress)
    }

    private suspend fun generationMoveTokenList(
        tokens: List<FlowCoin>,
        walletAddress: String
    ): List<MoveToken> {
        val balanceMap = cadenceQueryTokenListBalanceWithAddress(walletAddress)
        return tokens.map { coin ->
            if (coin.isFlowCoin()) {
                return@map MoveToken(getFlowCoinBalance(walletAddress), coin)
            }
            val balance = balanceMap?.get(coin.contractId()) ?: BigDecimal.ZERO
            MoveToken(balance, coin)
        }.toList()
    }


    private fun fetchCoinList(): List<FlowCoin> {
        val text = URL(getTokenListUrl()).readText()
        val list = Gson().fromJson(text, TokenList::class.java)
        coinList.clear()
        coinList.addAll(list.tokens.map { it.copy(type = FlowCoinType.CADENCE) })
        return list.tokens
    }

    override suspend fun getMoveToken(contractId: String): MoveToken? {
        val tokens = if (coinList.isEmpty()) {
            fetchCoinList()
        } else {
            coinList
        }
        val balanceMap = cadenceQueryTokenListBalanceWithAddress(WalletManager.selectedWalletAddress())
        return tokens.firstOrNull { it.contractId() == contractId }?.let { coin ->
            if (coin.isFlowCoin()) {
                MoveToken(getFlowCoinBalance(WalletManager.selectedWalletAddress()), coin)
            } else {
                val balance = balanceMap?.get(coin.contractId()) ?: BigDecimal.ZERO
                if (balance >= BigDecimal.ZERO) MoveToken(balance, coin) else null
            }
        }
    }

    override suspend fun getMoveToken(tokenId: String, walletAddress: String): MoveToken? {
        val tokens = if (coinList.isEmpty()) {
            fetchCoinList()
        } else {
            coinList
        }
        return generationMoveToken(tokens, tokenId, walletAddress)
    }

    private suspend fun getFlowCoinBalance(walletAddress: String): BigDecimal {
        return if (WalletManager.isChildAccount(walletAddress)) {
            cadenceQueryTokenBalanceWithAddress(FlowCoinListManager.getFlowCoin(), walletAddress)
        } else {
            AccountInfoManager.getCurrentFlowBalance() ?: cadenceQueryTokenBalanceWithAddress(FlowCoinListManager.getFlowCoin(), walletAddress)
        } ?: BigDecimal.ZERO
    }

    private suspend fun generationMoveToken(
        tokens: List<FlowCoin>,
        tokenId: String,
        walletAddress: String
    ): MoveToken? {
        val balanceMap = cadenceQueryTokenListBalanceWithAddress(walletAddress)
        return tokens.firstOrNull { it.getFTIdentifier() == tokenId }?.let { coin ->
            if (coin.isFlowCoin()) {
                MoveToken(getFlowCoinBalance(walletAddress), coin)
            } else {
                val balance = balanceMap?.get(coin.contractId()) ?: BigDecimal.ZERO
                if (balance >= BigDecimal.ZERO) MoveToken(balance, coin) else null
            }
        }
    }

    override fun getTokenListUrl(): String {
        return "https://raw.githubusercontent" +
                ".com/Outblock/token-list-jsons/outblock/jsons/${chainNetWorkString()}/flow/${getFileName()}"
    }

    override fun getFileName(): String {
        return if (isDev()) {
            "dev.json"
        } else {
            "default.json"
        }
    }
}


