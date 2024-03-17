package com.flowfoundation.wallet.utils


fun String.toCoverUrl(): String {
    if (startsWith("ipfs://")) {
        return replace("ipfs://", "https://ipfs.io/ipfs/")
    }
    return this
}