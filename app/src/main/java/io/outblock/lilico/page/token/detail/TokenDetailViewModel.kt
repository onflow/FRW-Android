package io.outblock.lilico.page.token.detail

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.outblock.lilico.cache.transferRecordCache
import io.outblock.lilico.manager.account.Balance
import io.outblock.lilico.manager.account.BalanceManager
import io.outblock.lilico.manager.account.OnBalanceUpdate
import io.outblock.lilico.manager.coin.CoinRateManager
import io.outblock.lilico.manager.coin.FlowCoin
import io.outblock.lilico.manager.coin.OnCoinRateUpdate
import io.outblock.lilico.manager.price.CurrencyManager
import io.outblock.lilico.manager.transaction.OnTransactionStateChange
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.flowscan.contractId
import io.outblock.lilico.network.model.CryptowatchSummaryData
import io.outblock.lilico.network.model.TransferRecord
import io.outblock.lilico.network.model.TransferRecordList
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.utils.getQuoteMarket
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.updateQuoteMarket
import io.outblock.lilico.utils.viewModelIOScope
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

class TokenDetailViewModel : ViewModel(), OnBalanceUpdate, OnCoinRateUpdate, OnTransactionStateChange {

    private lateinit var coin: FlowCoin

    val balanceAmountLiveData = MutableLiveData<Float>()
    val balancePriceLiveData = MutableLiveData<Float>()

    val chartDataLiveData = MutableLiveData<List<Quote>>()
    val chartLoadingLiveData = MutableLiveData<Boolean>()
    val summaryLiveData = MutableLiveData<CryptowatchSummaryData.Result>()
    val transferListLiveData = MutableLiveData<List<TransferRecord>>()

    private var coinRate = 0.0f

    private var period: Period? = null
    private var market: String? = null

    private val chartCache = ConcurrentHashMap<String, List<Quote>>()

    init {
        BalanceManager.addListener(this)
        CoinRateManager.addListener(this)
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    override fun onBalanceUpdate(coin: FlowCoin, balance: Balance) {
        if (coin.contractName == this.coin.contractName) {
            balanceAmountLiveData.value = balance.balance
            balancePriceLiveData.value = coinRate * balance.balance
        }
    }

    override fun onCoinRateUpdate(coin: FlowCoin, price: Float) {
        if (coin.contractName == this.coin.contractName) {
            coinRate = price
            balancePriceLiveData.value = coinRate * (balanceAmountLiveData.value ?: 0f)
        }
    }

    override fun onTransactionStateChange() {
        ioScope {
            delay(1000 * 5)
            transactionQuery()
        }
    }

    fun setCoin(coin: FlowCoin) {
        this.coin = coin
    }

    fun load() {
        BalanceManager.getBalanceByCoin(coin)
        CoinRateManager.fetchCoinRate(coin)
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
                    coinPair = coin.getPricePair(QuoteMarket.fromMarketName(market)),
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
            val result = service.summary(
                market = market,
                coinPair = coin.getPricePair(QuoteMarket.fromMarketName(market)),
            )
            if (this.period == period && this.market == market) {
                summaryLiveData.postValue(result.data.result)
            }
        }
    }

    private fun transactionQuery() {
        viewModelIOScope(this) {
            val cache = transferRecordCache(coin.contractId()).read()?.list
            if (!cache.isNullOrEmpty()) {
                transferListLiveData.postValue(cache.take(3))
            }

            val service = retrofit().create(ApiService::class.java)
            val walletAddress = WalletManager.selectedWalletAddress() ?: return@viewModelIOScope
            val resp = service.getTransferRecordByToken(walletAddress, coin.contractId(), limit = 3)
            val data = resp.data?.transactions.orEmpty()
            transferListLiveData.postValue(data)

            transferRecordCache(coin.contractId()).cache(TransferRecordList(data))
        }
    }
}