package com.flowfoundation.wallet.manager.coin

import android.text.format.DateUtils
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.cache.CacheManager
import com.flowfoundation.wallet.manager.coin.model.TokenPrice
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.token.detail.QuoteMarket
import com.flowfoundation.wallet.page.token.detail.getPricePair
import com.flowfoundation.wallet.page.token.detail.isUSDStableCoin
import com.flowfoundation.wallet.utils.getQuoteMarket
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import java.lang.ref.WeakReference
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object CoinRateManager {
    private val TAG = CoinRateManager::class.java.simpleName

    private var coinRateMap = ConcurrentHashMap<String, CoinRate>()

    private val listeners = CopyOnWriteArrayList<WeakReference<OnCoinRateUpdate>>()

    private val cache by lazy { CacheManager<CoinRateCacheData>("COIN_RATE", CoinRateCacheData::class.java) }

    fun init() {
        ioScope {
            coinRateMap = ConcurrentHashMap<String, CoinRate>(cache.read()?.data.orEmpty())
        }
    }

    fun refresh() {
        ioScope { fetchCoinListRate(FlowCoinListManager.getEnabledCoinList()) }
    }

    fun addListener(callback: OnCoinRateUpdate) {
        if (listeners.firstOrNull { it.get() == callback } != null) {
            return
        }
        uiScope { this.listeners.add(WeakReference(callback)) }
    }

    fun removeListener(callback: OnCoinRateUpdate) {
        uiScope { 
            listeners.removeAll { it.get() == callback || it.get() == null }
        }
    }

    fun coinRate(contractId: String) = coinRateMap[contractId]?.price

    fun fetchCoinRate(coin: FlowCoin) {
        ioScope {
            if (coin.isUSDStableCoin()) {
                dispatchListeners(coin, BigDecimal.ONE, 0f)
                return@ioScope
            }
            val cacheRate = coinRateMap[coin.contractId()]
            cacheRate?.let { dispatchListeners(coin, it.price, it.quoteChange) }
            if (cacheRate.isExpire()) {
                runCatching {
                    val apiService = retrofitApi().create(ApiService::class.java)
                    val tokenPriceResponse = apiService.getTokenPrices()
                    val tokenPriceList = tokenPriceResponse.data
                    val market = QuoteMarket.fromMarketName(getQuoteMarket())
                    val coinPair = coin.getPricePair(market)

                    if (coinPair.isEmpty()) {
                        tokenPriceList?.find {
                            coin.isSameCoin(it)
                        }?.let {
                            val rate = it.rateToUSD
                            updateCache(coin, rate, 0f)
                            dispatchListeners(coin, rate, 0f)
                        }
                        return@ioScope
                    }

                    val service = retrofit().create(ApiService::class.java)
                    val response = service.summary(market.value, coin.getPricePair(market))
                    val price = tokenPriceList?.find {
                        coin.isSameCoin(it)
                    }?.rateToUSD ?: response.data.result.price.last
                    val quoteChange = response.data.result.price.change.percentage
                    updateCache(coin, price, quoteChange)
                    dispatchListeners(coin, price, quoteChange)
                }
            }
        }
    }

    fun FlowCoin.isSameCoin(tokenPrice: TokenPrice): Boolean {
        return if (isFlowCoin()) {
            val result = isSameCoin(tokenPrice.contractAddress, tokenPrice.contractName)
            result
        } else {
            if (type == FlowCoinType.EVM) {
                // Clean up addresses for comparison by removing all possible prefixes and converting to lowercase
                val cleanAddress = address
                    .removePrefix("A.")  // Remove A. prefix
                    .removePrefix("0x")  // Remove 0x prefix if present
                    .removeSuffix(".")   // Remove trailing dot
                    .lowercase()         // Convert to lowercase
                
                val cleanEvmAddress = tokenPrice.evmAddress
                    ?.removePrefix("0x") // Remove 0x prefix if present
                    ?.lowercase()        // Convert to lowercase
                    ?: return false      // If evmAddress is null, no match
                    
                val result = cleanAddress == cleanEvmAddress
                result
            } else {
                val result = isSameCoin(tokenPrice.contractAddress, tokenPrice.contractName)
                result
            }
        }
    }

    fun fetchCoinListRate(list: List<FlowCoin>) {
        ioScope {
            val apiService = retrofitApi().create(ApiService::class.java)
            val tokenPriceResponse = apiService.getTokenPrices()
            val tokenPriceList = tokenPriceResponse.data
            
            list.forEach { coin ->
                if (coin.isUSDStableCoin()) {
                    dispatchListeners(coin, BigDecimal.ONE, 0f)
                    return@forEach
                }
                val cacheRate = coinRateMap[coin.contractId()]
                cacheRate?.let { 
                    dispatchListeners(coin, it.price, it.quoteChange)
                }
                if (cacheRate.isExpire()) {
                    runCatching {
                        val market = QuoteMarket.fromMarketName(getQuoteMarket())
                        val coinPair = coin.getPricePair(market)

                        if (coinPair.isEmpty()) {
                            tokenPriceList?.find {
                                val isSame = coin.isSameCoin(it)
                                isSame
                            }?.let {
                                val rate = it.rateToUSD
                                updateCache(coin, rate, 0f)
                                dispatchListeners(coin, rate, 0f)
                            } ?: run {
                            }
                            return@forEach
                        }

                        val service = retrofit().create(ApiService::class.java)
                        val response = service.summary(market.value, coin.getPricePair(market))
                        val price = tokenPriceList?.find {
                            coin.isSameCoin(it)
                        }?.rateToUSD ?: response.data.result.price.last
                        val quoteChange = response.data.result.price.change.percentage
                        updateCache(coin, price, quoteChange)
                        dispatchListeners(coin, price, quoteChange)
                    }.onFailure { error ->
                        logd(TAG, "Error fetching rate for ${coin.symbol}: ${error.message}")
                    }
                }
            }
        }
    }

    private fun CoinRate?.isExpire(): Boolean = this == null || System.currentTimeMillis() - updateTime > 30 * DateUtils.SECOND_IN_MILLIS

    private fun updateCache(coin: FlowCoin, price: BigDecimal, quoteChange: Float) {
        ioScope {
            coinRateMap[coin.contractId()] = CoinRate(coin.symbol, coin.contractId(), price, quoteChange, System.currentTimeMillis())
            cache.cache(CoinRateCacheData(coinRateMap))
        }
    }

    private fun dispatchListeners(coin: FlowCoin, price: BigDecimal, quoteChange: Float) {
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onCoinRateUpdate(coin, price, quoteChange) }
        }
    }
}

interface OnCoinRateUpdate {
    fun onCoinRateUpdate(coin: FlowCoin, price: BigDecimal) {

    }

    fun onCoinRateUpdate(coin: FlowCoin, price: BigDecimal, quoteChange: Float) {
        onCoinRateUpdate(coin, price)
    }
}

private class CoinRateCacheData(
    @SerializedName("data")
    var data: Map<String, CoinRate>,
)

class CoinRate(
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("contractId")
    val contractId: String? = "",
    @SerializedName("price")
    val price: BigDecimal,
    @SerializedName("quoteChange")
    val quoteChange: Float = 0f,
    @SerializedName("updateTime")
    val updateTime: Long,
)