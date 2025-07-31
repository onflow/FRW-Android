package com.flowfoundation.wallet.page.token.detail

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.cache.transferRecordCache
import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.token.FungibleTokenUpdateListener
import com.flowfoundation.wallet.manager.token.model.FungibleToken
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.CryptowatchSummaryData
import com.flowfoundation.wallet.network.model.TransferRecord
import com.flowfoundation.wallet.network.model.TransferRecordList
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.utils.getQuoteMarket
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.updateQuoteMarket
import com.flowfoundation.wallet.utils.viewModelIOScope
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

class TokenDetailViewModel : ViewModel(), OnTransactionStateChange, FungibleTokenUpdateListener {

    private lateinit var token: FungibleToken

    val balanceAmountLiveData = MutableLiveData<BigDecimal>()
    val balancePriceLiveData = MutableLiveData<BigDecimal>()

    val chartDataLiveData = MutableLiveData<List<Quote>>()
    private val chartLoadingLiveData = MutableLiveData<Boolean>()
    val summaryLiveData = MutableLiveData<CryptowatchSummaryData.Result>()
    val transferListLiveData = MutableLiveData<List<TransferRecord>>()

    private var coinRate: BigDecimal = BigDecimal.ZERO

    private var period: Period? = null
    private var market: String? = null

    private val chartCache = ConcurrentHashMap<String, List<Quote>>()

    init {
        FungibleTokenListManager.addTokenUpdateListener(this)
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    override fun onTokenUpdated(token: FungibleToken) {
        if (this.token.isSameToken(token.contractId())) {
            balanceAmountLiveData.value = token.tokenBalance()
            balancePriceLiveData.value = token.tokenBalancePrice()
            coinRate = token.tokenPrice()
        }
    }

    override fun onTransactionStateChange() {
        ioScope {
            delay(1000 * 5)
            transactionQuery()
        }
    }

    fun setToken(token: FungibleToken) {
        this.token = token
    }

    fun load() {
        viewModelIOScope(this) {
            FungibleTokenListManager.updateTokenInfo(token.contractId())
        }
        transactionQuery()
    }

    fun changePeriod(period: Period) {
        this.period = period
        refreshCharData(period)
        refreshSummary(period)
    }

    fun changeMarket(market: String) {
        ioScope {
            updateQuoteMarket(market)
            refreshCharData(this.period!!)
            refreshSummary(this.period!!)
        }
    }

    private fun refreshCharData(period: Period) {
        viewModelIOScope(this) {
            val market = getQuoteMarket()
            this.market = market

            if (chartCache[period.value + market] != null) {
                chartDataLiveData.postValue(chartCache[period.value + market])
                return@viewModelIOScope
            }

            chartLoadingLiveData.postValue(true)
            runCatching {
                val service = retrofit().create(ApiService::class.java)
                val result = service.ohlc(
                    market = market,
                    coinPair = token.getPricePair(QuoteMarket.fromMarketName(market)),
                    after = if (period == Period.ALL) null else period.getChartPeriodTs(),
                    periods = "${period.getChartPeriodFrequency()}",
                )
                val currency = CurrencyManager.currencyPrice()
                val data = result.parseMarketQuoteData(period).map { it.copy(closePrice = it.closePrice * currency) }
                chartCache[period.value + market] = data
                if (this.period == period && this.market == market) {
                    chartDataLiveData.postValue(data)
                }
            }
            chartLoadingLiveData.postValue(false)
        }
    }

    private fun refreshSummary(period: Period) {
        viewModelIOScope(this) {
            val market = getQuoteMarket()
            val service = retrofit().create(ApiService::class.java)
            val coinPair = token.getPricePair(QuoteMarket.fromMarketName(market))
            if (coinPair.isEmpty()) {
                return@viewModelIOScope
            }
            val result = service.summary(
                market = market,
                coinPair = coinPair,
            )
            if (this.period == period && this.market == market) {
                summaryLiveData.postValue(result.data.result)
            }
        }
    }

    private fun transactionQuery() {
        if (WalletManager.isEVMAccountSelected()) {
            return
        }
        viewModelIOScope(this) {
            val cache = transferRecordCache(token.contractId()).read()?.list
            if (!cache.isNullOrEmpty()) {
                transferListLiveData.postValue(cache.take(3))
            }

            val service = retrofitApi().create(ApiService::class.java)
            val walletAddress = WalletManager.selectedWalletAddress()
            val resp = service.getTransferRecordByToken(walletAddress, token.tokenIdentifier(), limit = 3)
            val data = resp.data?.transactions.orEmpty()
            transferListLiveData.postValue(data)

            transferRecordCache(token.contractId()).cache(TransferRecordList(data))
        }
    }
}