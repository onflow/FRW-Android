package com.flowfoundation.wallet.manager.walletconnect.model

import com.reown.android.Core
import com.reown.sign.client.Sign

class WCRequest(
    val metaData: Core.Model.AppMetaData?,
    val requestId: Long,
    val chainId: String?,
    val method: String,
    val params: String,
    val topic: String,
)

fun Sign.Model.SessionRequest.toWcRequest(): WCRequest {
    return WCRequest(
        metaData = peerMetaData,
        requestId = request.id,
        params = request.params,
        method = request.method,
        topic = topic,
        chainId = chainId,
    )
}