package com.flowfoundation.wallet.page.profile.subpage.currency.model

import androidx.annotation.DrawableRes
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.price.CurrencyManager

enum class Currency(
    val title: String,
    val symbol: String,
    val flag: String,
    @DrawableRes val icon: Int,
) {
    USD("United States Dollar", "$", "🇺🇸", R.drawable.ic_currency_usd),
    EUR("Euro", "€", "🇪🇺", R.drawable.ic_currency_eur),
    CNY("Chinese Yuan", "¥", "🇨🇳", R.drawable.ic_currency_cny),
    AUD("Australian Dollar ", "$", "🇦🇺", R.drawable.ic_currency_aud),
    CAD("Canadian Dollar", "$", "🇨🇦", R.drawable.ic_currency_cad),
    KRW("South Korean Won", "₩", "🇰🇷", R.drawable.ic_currency_krw),
    HKD("Hong Kong Dollar", "$", "🇭🇰", R.drawable.ic_currency_hkd),
    SGD("Singapore Dollar", "$", "🇸🇬", R.drawable.ic_currency_sgd),
    RUB("Russian Ruble", "₽", "🇷🇺", R.drawable.ic_currency_rub),
    JPY("Japanese Yen", "¥", "🇯🇵", R.drawable.ic_currency_jpy),
    TWD("New Taiwan Dollar", "$", "🇹🇼", R.drawable.ic_currency_twd),
    CHF("Swiss Franc", "Fr", "🇨🇭", R.drawable.ic_currency_chf),
    MXN("Mexican Peso", "$", "🇲🇽", R.drawable.ic_currency_mxn),
}

fun findCurrencyFromFlag(flag: String): Currency {
    return Currency.values().firstOrNull { it.flag == flag } ?: Currency.USD
}

fun selectedCurrency(): Currency {
    return findCurrencyFromFlag(CurrencyManager.currencyFlag())
}
