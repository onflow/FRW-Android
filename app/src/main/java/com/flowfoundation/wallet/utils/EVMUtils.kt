package com.flowfoundation.wallet.utils

fun shortenEVMString(input: String?): String {
    if (input == null) {
        return ""
    }
    if (evmAddressPattern.matches(input).not()) {
        return input
    }
    if (input.length <= 12) {
        return input
    }
    val prefix = input.substring(0, 7)
    val suffix = input.substring(input.length - 5)
    return "$prefix...$suffix"
}