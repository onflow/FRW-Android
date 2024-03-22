package com.flowfoundation.wallet.utils

import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat


fun Long.formatBalance(): Float {
    val currencyPrice = BigDecimal(9.08)
    val flowDecimal = BigDecimal(10).pow(8)
    val balance = BigDecimal(this).divide(flowDecimal)
    val currencyBalance = balance.multiply(currencyPrice)
    return balance.setScale(3, BigDecimal.ROUND_HALF_DOWN).toFloat()
}

fun Float.formatPrice(
    digits: Int = 3,
    convertCurrency: Boolean = true,
    includeSymbol: Boolean = false,
    includeSymbolSpace: Boolean = false,
): String {
    var value = this
    if (convertCurrency) {
        if (CurrencyManager.currencyPrice() < 0) {
            return "-"
        }
        value *= CurrencyManager.currencyPrice()
    }
    val format = value.format(digits)
    return if (includeSymbol) "${selectedCurrency().symbol}${if (includeSymbolSpace) " " else ""}$format" else format
}

fun Float.format(digits: Int = 3, roundingMode: RoundingMode = RoundingMode.DOWN): String {
    return DecimalFormat("0.${"#".repeat(digits)}").apply { setRoundingMode(roundingMode) }.format(this)
}

fun Float.formatNum(
    digits: Int = 3,
    roundingMode: RoundingMode = RoundingMode.DOWN,
): String {
    return format(digits, roundingMode)
}