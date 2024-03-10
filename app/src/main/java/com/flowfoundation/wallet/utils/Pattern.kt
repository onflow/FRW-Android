package com.flowfoundation.wallet.utils

val addressPattern by lazy { Regex("^0x[a-fA-F0-9]{16}\$") }