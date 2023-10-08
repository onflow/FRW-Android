package io.outblock.lilico.manager.price

import com.google.gson.annotations.SerializedName
import io.outblock.lilico.cache.currencyCache
import io.outblock.lilico.network.ApiService
import io.outblock.lilico.network.retrofit
import io.outblock.lilico.page.profile.subpage.currency.model.Currency
import io.outblock.lilico.page.profile.subpage.currency.model.findCurrencyFromFlag
import io.outblock.lilico.utils.getCurrencyFlag
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.uiScope
import java.lang.ref.WeakReference

object CurrencyManager {
    private var flag = ""

    private val currencyMap = mutableMapOf<String, Float>()

    private val listeners = mutableListOf<WeakReference<CurrencyUpdateListener>>()

    fun currencyFlag() = flag

    fun init() {
        ioScope {
            flag = getCurrencyFlag()
            currencyMap.putAll(currencyCache().read()?.data?.associate { it.flag to it.price }.orEmpty())
            fetchInternal(flag)
        }
    }

    fun currencyPrice(): Float {
        return currencyPriceInternal(flag)
    }

    fun fetch() {
        ioScope { fetchInternal(getCurrencyFlag()) }
    }

    fun addCurrencyUpdateListener(listener: CurrencyUpdateListener) {
        listeners.add(WeakReference(listener))
    }

    fun updateCurrency(flag: String) {
        this.flag = flag
        fetchInternal(flag)
        ioScope { dispatchListener(flag, currencyPriceInternal(flag)) }
    }

    private suspend fun currency(): Currency {
        return findCurrencyFromFlag(flag.ifBlank { getCurrencyFlag() })
    }

    private fun currencyPriceInternal(flag: String): Float = currencyMap[flag] ?: -1.0f

    private fun fetchInternal(flag: String) {
        ioScope {
            val service = retrofit().create(ApiService::class.java)
            val response = service.currency(findCurrencyFromFlag(flag).name)
            if (response.data.result > 0) {
                currencyMap[flag] = response.data.result
                currencyCache().cache(CurrencyCache(currencyMap.map { CurrencyPrice(it.key, it.value) }))
                dispatchListener(flag, response.data.result)
            }
        }
    }

    private fun dispatchListener(flag: String, price: Float) {
        uiScope {
            listeners.forEach { it.get()?.onCurrencyUpdate(flag, price) }
            listeners.removeAll { it.get() == null }
        }
    }
}

interface CurrencyUpdateListener {
    fun onCurrencyUpdate(flag: String, price: Float)
}

class CurrencyCache(
    @SerializedName("data")
    val data: List<CurrencyPrice>
)

class CurrencyPrice(
    @SerializedName("flag")
    val flag: String,
    @SerializedName("price")
    val price: Float,
)