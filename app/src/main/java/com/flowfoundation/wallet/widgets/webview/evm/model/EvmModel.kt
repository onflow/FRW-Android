package com.flowfoundation.wallet.widgets.webview.evm.model

import kotlinx.serialization.Serializable

@Serializable
data class EvmEvent (
    val transactionHash: String,
)

@Serializable
data class EvmTransaction(
    val value: String?,
    val to: String?,
    val gas: String?,
    val data: String?,
    val from: String?
)