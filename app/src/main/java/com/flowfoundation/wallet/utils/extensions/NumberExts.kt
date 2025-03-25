package com.flowfoundation.wallet.utils.extensions

import java.math.BigDecimal

fun Int?.orZero() = this ?: 0

fun String?.toSafeInt(default: Int = 0): Int {
    if (this.isNullOrBlank()) {
        return default
    }

    return this.toIntOrNull() ?: default
}

fun String?.toSafeFloat(default: Float = 0f): Float {
    if (this.isNullOrBlank()) {
        return default
    }

    return this.toFloatOrNull() ?: default
}

fun String?.toSafeDouble(default: Double = 0.0): Double {
    return this?.toDoubleOrNull() ?: default
}

fun String?.toSafeDecimal(default: BigDecimal = BigDecimal.ZERO): BigDecimal {
    if (this.isNullOrBlank()) {
        return default
    }
    return this.toBigDecimalOrNull() ?: default
}
