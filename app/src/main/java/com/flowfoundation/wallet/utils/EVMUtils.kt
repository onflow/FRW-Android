package com.flowfoundation.wallet.utils

fun shortenEVMString(input: String?): String {
    if (input == null) {
        return ""
    }
    if (evmAddressPattern.matches(input).not()) {
        return input
    }
    if (input.length <= 18) {
        return input
    }
    val prefix = input.substring(0, 9)
    val suffix = input.substring(input.length - 7)
    return "$prefix...$suffix"
}