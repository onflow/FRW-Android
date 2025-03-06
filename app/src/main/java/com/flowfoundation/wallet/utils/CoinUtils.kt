package com.flowfoundation.wallet.utils

import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

fun Float.formatPrice(
    digits: Int = 3,
    convertCurrency: Boolean = true,
    includeSymbol: Boolean = false,
    includeSymbolSpace: Boolean = false,
    isAbbreviation: Boolean = false
): String {
    var value = this
    if (convertCurrency) {
        if (CurrencyManager.currencyPrice() < 0) {
            return "-"
        }
        value *= CurrencyManager.currencyPrice()
    }

    if (value < 0.01f) {
        return formatTokenPrice(value.toDouble())
    }

    val format = if (value < 1_000_000) {
        value.format(digits)
    } else if (isAbbreviation) {
        value.toDouble().formatLargeNumber()
    } else {
        value.toDouble().formatNumberWithCommas()
    }
    return if (includeSymbol) "${selectedCurrency().symbol}${if (includeSymbolSpace) " " else ""}$format" else format
}

fun BigDecimal.formatPrice(
    digits: Int = 3,
    convertCurrency: Boolean = true,
    includeSymbol: Boolean = false,
    includeSymbolSpace: Boolean = false,
    isAbbreviation: Boolean = false
): String {
    var value = this
    if (convertCurrency) {
        if (CurrencyManager.currencyDecimalPrice() < BigDecimal.ZERO) {
            return "-"
        }
        value *= CurrencyManager.currencyDecimalPrice()
    }

    // For token prices less than one cent, use token formatting.
    if (value < BigDecimal("0.01")) {
        return formatTokenPrice(value.toDouble())
    }

    val format = if (value < BigDecimal("1000000")) {
        value.format(digits)
    } else if (isAbbreviation) {
        value.formatLargeNumber()
    } else {
        value.formatNumberWithCommas()
    }
    return if (includeSymbol) "${selectedCurrency().symbol}${if (includeSymbolSpace) " " else ""}$format" else format
}

fun BigDecimal.format(
    digits: Int = 3, roundingMode: RoundingMode = RoundingMode.DOWN
): String {
    return this.setScale(digits, roundingMode).stripTrailingZeros().toPlainString()
}

fun BigDecimal.formatLargeBalanceNumber(isAbbreviation: Boolean = false): String {
    return if (this < BigDecimal("1000000")) {
        this.format()
    } else if (isAbbreviation) {
        this.formatLargeNumber()
    } else {
        this.formatNumberWithCommas()
    }
}

fun BigDecimal.formatLargeNumber(): String {
    val decimalFormat = DecimalFormat("0.###")
    decimalFormat.roundingMode = RoundingMode.DOWN
    return when {
        this >= BigDecimal("1000000000000") -> decimalFormat.format(this.divide(BigDecimal("1000000000000"))) + "t"
        this >= BigDecimal("1000000000") -> decimalFormat.format(this.divide(BigDecimal("1000000000"))) + "b"
        this >= BigDecimal("1000000") -> decimalFormat.format(this.divide(BigDecimal("1000000"))) + "m"
        else -> this.format()
    }
}

fun BigDecimal.formatNumberWithCommas(): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US)
    return formatter.format(this)
}

fun Float.format(digits: Int = 3, roundingMode: RoundingMode = RoundingMode.DOWN): String {
    return DecimalFormat("0.${"#".repeat(digits)}").apply { setRoundingMode(roundingMode) }
        .format(this)
}

fun Float.formatNum(
    digits: Int = 3,
    roundingMode: RoundingMode = RoundingMode.DOWN,
): String {
    return format(digits, roundingMode)
}

fun Float.formatLargeBalanceNumber(isAbbreviation: Boolean = false): String {
    return if (this < 1_000_000) {
        this.format()
    } else if (isAbbreviation) {
        this.toDouble().formatLargeNumber()
    } else {
        this.toDouble().formatNumberWithCommas()
    }
}

fun Double.formatLargeNumber(): String {
    val decimalFormat = DecimalFormat("0.###")
    decimalFormat.roundingMode = RoundingMode.DOWN
    return when {
        this >= 1_000_000_000_000 -> decimalFormat.format(this / 1_000_000_000_000) + "t"
        this >= 1_000_000_000 -> decimalFormat.format(this / 1_000_000_000) + "b"
        this >= 1_000_000 -> decimalFormat.format(this / 1_000_000) + "m"
        else -> this.toString()
    }
}

fun Double.formatNumberWithCommas(): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US)
    return formatter.format(this)
}

fun Double.formatPrice(
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

    // For token prices less than one cent, use token formatting.
    if (value < 0.01) {
        return formatTokenPrice(value)
    }

    val format = value.format(digits)
    return if (includeSymbol) "${selectedCurrency().symbol}${if (includeSymbolSpace) " " else ""}$format" else format
}

fun Double.format(digits: Int = 3, roundingMode: RoundingMode = RoundingMode.DOWN): String {
    return DecimalFormat("0.${"#".repeat(digits)}").apply { setRoundingMode(roundingMode) }
        .format(this)
}

fun Double.formatNum(
    digits: Int = 3,
    roundingMode: RoundingMode = RoundingMode.DOWN,
): String {
    return format(digits, roundingMode)
}

// Helper: converts an integer to its subscript representation.
private fun toSubscript(n: Int): String {
    val subscripts = mapOf(
        '0' to '₀',
        '1' to '₁',
        '2' to '₂',
        '3' to '₃',
        '4' to '₄',
        '5' to '₅',
        '6' to '₆',
        '7' to '₇',
        '8' to '₈',
        '9' to '₉'
    )
    return n.toString().map { subscripts[it] ?: it }.joinToString("")
}

fun formatTokenPrice(price: Double): String {
    if (price == 0.0) {
        return "$0.00"
    }

    // For prices at least $0.0001, use normal formatting with min 2 and max 4 decimals.
    if (price >= 0.0001) {
        // Use BigDecimal for precise rounding. Scale to 4, rounding up.
        val bd = BigDecimal(price).setScale(4, RoundingMode.UP)
        var formatted = bd.toPlainString()
        // Ensure a decimal point exists and we have at least 2 decimals.
        if (!formatted.contains(".")) {
            formatted += ".00"
        } else {
            val parts = formatted.split(".")
            var decimals = parts[1]
            // Pad with zeros if needed.
            if (decimals.length < 2) {
                decimals = decimals.padEnd(2, '0')
            } else if (decimals.length > 2) {
                // Remove trailing zeros—but do not drop below 2 decimals.
                decimals = decimals.trimEnd('0')
                if (decimals.length < 2) {
                    decimals = decimals.padEnd(2, '0')
                }
            }
            formatted = parts[0] + "." + decimals
        }
        return "$$formatted"
    } else {
        // For prices below $0.0001, apply subscript formatting.
        val bd = BigDecimal(price).setScale(10, RoundingMode.UP)
        val priceStr = bd.toPlainString() // e.g., "0.0000033450"
        val fraction = priceStr.substringAfter(".")

        val leadingZerosCount = fraction.takeWhile { it == '0' }.length
        val nonZeroPart = fraction.drop(leadingZerosCount)

        val significant = if (nonZeroPart.length > 3) {
            // Construct a BigDecimal from "0.<nonZeroPart>" and round to 3 decimals.
            BigDecimal("0.$nonZeroPart").setScale(3, RoundingMode.UP).toPlainString()
                .substringAfter(".")
        } else {
            nonZeroPart
        }

        val subscriptZeros = toSubscript(leadingZerosCount)
        return "$0.0$subscriptZeros$significant"

    }
}