package com.flowfoundation.wallet.reactnative.bridge

import java.util.concurrent.ConcurrentHashMap

object NativeRequestRegistry {
    private val callbacks = ConcurrentHashMap<String, (NativeRequestResult) -> Unit>()

    fun register(requestId: String, callback: (NativeRequestResult) -> Unit) {
        callbacks[requestId] = callback
    }

    fun handle(result: NativeRequestResult) {
        val callback = callbacks.remove(result.requestId)
        callback?.invoke(result)
    }
}

data class NativeRequestResult(
    val requestId: String,
    val eventName: String,
    val resultJson: String?,
    val error: String?
)
