package com.flowfoundation.wallet.page.token.detail.provider

import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.coin.CustomTokenManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinType
import com.flowfoundation.wallet.manager.coin.TokenList
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryCOATokenBalance
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.token.detail.model.MoveToken
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.readTextFromAssets
import com.google.gson.Gson
import java.math.BigDecimal
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList


class EVMAccountTokenProvider : MoveTokenProvider {

    private val TAG = EVMAccountTokenProvider::class.java.simpleName
    private val coinList = CopyOnWriteArrayList<FlowCoin>()

    init {
        ioScope {
            fetchCoinList()
        }
    }

    private fun fetchCoinList(): List<FlowCoin> {
        val text = URL(getTokenListUrl()).readText()
        val list = Gson().fromJson(text, TokenList::class.java)
        coinList.clear()
        coinList.addAll(list.tokens.map { it.copy(type = FlowCoinType.EVM) })
        addFlowTokenManually()
        addCustomToken()
        return coinList.toList()
    }

    private fun addFlowTokenManually() {
        try {
            val text = readTextFromAssets(
                if (isTestnet()) {
                    "config/flow_token_testnet.json"
                } else {
                    "config/flow_token_mainnet.json"
                }
            )
            Gson().fromJson(text, FlowCoin::class.java)?.let { coin ->
                if (coinList.none { it.address == coin.address }) {
                    coinList.add(0, coin.copy(type = FlowCoinType.EVM))
                }
            }
        } catch (e: Exception) {
            loge(TAG, "manually add flow token failure :: $e")
        }
    }

    private fun addCustomToken() {
        val list = CustomTokenManager.getCurrentCustomTokenList()
        val existingAddresses = coinList.map { it.address.lowercase() }.toSet()
        coinList.addAll(list.map {
            it.toFlowCoin()
        }.filter { it.address !in existingAddresses }.toList())
    }

    override suspend fun getMoveTokenList(walletAddress: String): List<MoveToken> {
        val tokens = if (coinList.isEmpty()) {
            fetchCoinList()
        } else {
            coinList
        }
        val coinMap = tokens.associateBy { it.address.lowercase() }
        val apiService = retrofitApi().create(ApiService::class.java)
        val balanceResponse = apiService.getEVMTokenBalance(walletAddress, chainNetWorkString())
        val moveTokens = mutableListOf<MoveToken>()
        tokens.firstOrNull { it.isFlowCoin() }?.let { flowCoin ->
            moveTokens.add(MoveToken(getFlowCoinBalance(), flowCoin))
        }
        moveTokens.addAll(
            balanceResponse.data?.mapNotNull { evmBalance ->
                coinMap[evmBalance.address.lowercase()]?.let { coin ->
                    val balance = evmBalance.balance.toBigDecimal().movePointLeft(evmBalance.decimal)
                    MoveToken(balance, coin)
                }
            } ?: emptyList()
        )
        return moveTokens
    }

    override suspend fun getMoveToken(contractId: String): MoveToken? {
        val tokens = if (coinList.isEmpty()) {
            fetchCoinList()
        } else {
            coinList
        }
        val coin = tokens.firstOrNull { it.contractId() == contractId } ?: return null
        if (coin.isFlowCoin()) {
            return MoveToken(getFlowCoinBalance(), coin)
        }
        val apiService = retrofitApi().create(ApiService::class.java)
        val balanceResponse = apiService.getEVMTokenBalance(WalletManager.selectedWalletAddress(), chainNetWorkString())
        return balanceResponse.data?.firstOrNull { it.address.equals(coin.address, true) }?.let { evmBalance ->
            val balance = evmBalance.balance.toBigDecimal().movePointLeft(evmBalance.decimal)
            MoveToken(balance, coin)
        }
    }

    override suspend fun getMoveToken(tokenId: String, walletAddress: String): MoveToken? {
        val tokens = if (coinList.isEmpty()) {
            fetchCoinList()
        } else {
            coinList
        }
        val coin = tokens.firstOrNull { it.address == tokenId } ?: return null
        if (coin.isFlowCoin()) {
            return MoveToken(getFlowCoinBalance(), coin)
        }
        val apiService = retrofitApi().create(ApiService::class.java)
        val balanceResponse = apiService.getEVMTokenBalance(walletAddress, chainNetWorkString())
        return balanceResponse.data?.firstOrNull { it.address.equals(coin.address, true) }?.let { evmBalance ->
            val balance = evmBalance.balance.toBigDecimal().movePointLeft(evmBalance.decimal)
            MoveToken(balance, coin)
        }
    }

    private suspend fun getFlowCoinBalance(): BigDecimal {
        return cadenceQueryCOATokenBalance() ?: BigDecimal.ZERO
    }

    override fun getTokenListUrl(): String {
        return "https://raw.githubusercontent" +
                ".com/Outblock/token-list-jsons/outblock/jsons/${chainNetWorkString()}/evm/${getFileName()}"
    }

    override fun getFileName(): String {
        return "default.json"
    }
}