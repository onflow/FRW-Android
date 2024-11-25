package com.flowfoundation.wallet.page.inbox

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.nftco.flow.sdk.FlowTransactionStatus
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.cache.inboxCache
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.OnCoinRateUpdate
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.flowjvm.cadenceClaimInboxNft
import com.flowfoundation.wallet.manager.flowjvm.cadenceClaimInboxToken
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.network.OtherHostService
import com.flowfoundation.wallet.network.model.InboxNft
import com.flowfoundation.wallet.network.model.InboxToken
import com.flowfoundation.wallet.network.retrofitWithHost
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.toSafeInt
import java.math.BigDecimal

class InboxViewModel : ViewModel(), OnCoinRateUpdate, OnTransactionStateChange {

    val tokenListLiveData = MutableLiveData<List<InboxToken>>()
    val nftListLiveData = MutableLiveData<List<InboxNft>>()

    val claimExecutingLiveData = MutableLiveData<Boolean>()

    init {
        CoinRateManager.addListener(this)
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    override fun onTransactionStateChange() {
        query()
    }

    fun query() {
        viewModelIOScope(this) {
            queryCache()
            queryServer()
        }
    }

    override fun onCoinRateUpdate(coin: FlowCoin, price: BigDecimal) {
        val tokens = tokenListLiveData.value.orEmpty().toMutableList()
        tokens.toList().forEachIndexed { index, token ->
            if (token.coinAddress == coin.address) {
                tokens[index] = token.copy(marketValue = token.amount * price)
            }
        }
        tokenListLiveData.postValue(tokens)
    }

    fun claimToken(token: InboxToken) {
        claimExecutingLiveData.postValue(true)
        viewModelIOScope(this) {
            try {
                val coin = FlowCoinListManager.coinList().firstOrNull { it.address == token.coinAddress }!!
                val txid = cadenceClaimInboxToken(meowDomainHost()!!, token.key, coin, token.amount)!!
                val transactionState = TransactionState(
                    transactionId = txid,
                    time = System.currentTimeMillis(),
                    state = FlowTransactionStatus.PENDING.num,
                    type = TransactionState.TYPE_TRANSACTION_DEFAULT,
                    data = Gson().toJson(token)
                )
                TransactionStateManager.newTransaction(transactionState)
                pushBubbleStack(transactionState)
            } catch (e: Exception) {
                loge(e)
                toast(msgRes = R.string.claim_failed)
            }
            claimExecutingLiveData.postValue(false)
        }
    }

    fun claimNft(nft: InboxNft) {
        claimExecutingLiveData.postValue(true)
        viewModelIOScope(this) {
            try {
                val collection = NftCollectionConfig.get(nft.collectionAddress, nft.collectionName)!!
                val txid = cadenceClaimInboxNft(meowDomainHost()!!, nft.key, collection, nft.tokenId.toSafeInt())!!
                val transactionState = TransactionState(
                    transactionId = txid,
                    time = System.currentTimeMillis(),
                    state = FlowTransactionStatus.PENDING.num,
                    type = TransactionState.TYPE_TRANSACTION_DEFAULT,
                    data = Gson().toJson(nft)
                )
                TransactionStateManager.newTransaction(transactionState)
                pushBubbleStack(transactionState)
            } catch (e: Exception) {
                loge(e)
                toast(msgRes = R.string.claim_failed)
            }
            claimExecutingLiveData.postValue(false)
        }
    }

    private suspend fun queryServer() {
        val domain = meowDomain() ?: return
        val service = retrofitWithHost(if (isTestnet()) "https://testnet.flowns.io/" else "https://flowns.io").create(OtherHostService::class.java)
        val response = service.queryInbox(domain)
        tokenListLiveData.postValue(response.tokenList())
        nftListLiveData.postValue(response.nftList())
        updateInboxReadList(response)
        CoinRateManager.fetchCoinListRate(response.tokenList().mapNotNull { token -> FlowCoinListManager.coinList().firstOrNull { it.address == token.coinAddress } })
        inboxCache().cache(response)
    }

    private fun queryCache() {
        val response = inboxCache().read() ?: return
        tokenListLiveData.postValue(response.tokenList())
        nftListLiveData.postValue(response.nftList())
        CoinRateManager.fetchCoinListRate(response.tokenList().mapNotNull { token -> FlowCoinListManager.coinList().firstOrNull { it.address == token.coinAddress } })
    }
}