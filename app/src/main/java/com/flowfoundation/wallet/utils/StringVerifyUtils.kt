package com.flowfoundation.wallet.utils

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.res2String

private val USERNAME_REGEX by lazy { Regex("^[A-Za-z0-9]{3,15}\$") }

private const val USERNAME_MAX_SIZE = 15
private const val USERNAME_MIN_SIZE = 3

fun verifyPassword(password: String): Boolean {
    return password.length >= 8
}


fun usernameVerify(username: String): String? {
    return when {
        username.length < USERNAME_MIN_SIZE -> R.string.username_too_short.res2String()
        username.length > USERNAME_MAX_SIZE -> R.string.username_too_long.res2String()
        !USERNAME_REGEX.matches(username) -> R.string.username_format_wrong.res2String()
        else -> null
    }
}