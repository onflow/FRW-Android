package com.flowfoundation.wallet.manager.walletconnect

import com.flowfoundation.wallet.manager.app.EVM_MAINNET
import com.flowfoundation.wallet.manager.app.EVM_TESTNET
import com.flowfoundation.wallet.manager.app.flowChainNetworkString
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletconnect.model.WCRequest
import com.flowfoundation.wallet.manager.walletconnect.model.WalletConnectMethod
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.google.gson.annotations.SerializedName
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient

private const val TAG = "WalletConnectUtils"
private const val ETHEREUM_NETWORK = "eip155"

private val supportedChain = setOf(EVM_MAINNET, EVM_TESTNET)

fun Sign.Model.SessionProposal.approveSession() {
    val namespaces = mutableMapOf<String, Sign.Model.Namespace.Session>()
    namespaces.putAll(requiredNamespaces.map { item ->
        pair(item)
    }.toMap())
    namespaces.putAll(optionalNamespaces.map { item ->
        pair(item)
    }.toMap())
    logd(TAG, "approveSession: $namespaces")
    SignClient.approveSession(Sign.Params.Approve(proposerPublicKey, namespaces)) { error -> loge(error.throwable) }
}

private fun pair(
    item: Map.Entry<String, Sign.Model.Namespace.Proposal>
): Pair<String, Sign.Model.Namespace.Session> {
    val caip2Namespace = item.key
    val proposalNamespace = item.value
    val chains = if (caip2Namespace.lowercase() == ETHEREUM_NETWORK) {
        proposalNamespace.chains?.filter { it in supportedChain }.orEmpty()
    } else {
        proposalNamespace.chains.orEmpty()
    }
    val accounts = if (caip2Namespace.lowercase() == ETHEREUM_NETWORK) {
        chains.mapNotNull {
            val evmAddress = EVMWalletManager.getEVMAddress(flowChainNetworkString(it)).orEmpty()
            if (evmAddress.isNotEmpty()) {
                "$it:${evmAddress}"
            } else {
                null
            }
        }.toList()
    } else {
        chains.map { "$it:${WalletManager.wallet()?.walletAddress().orEmpty()}" }.toList()
    }
    val methods = if (caip2Namespace.lowercase() == ETHEREUM_NETWORK) {
        WalletConnectMethod.getSupportedEVMMethod()
    } else {
        WalletConnectMethod.getSupportedFlowMethod()
    }
    return caip2Namespace to Sign.Model.Namespace.Session(
        chains = chains,
        accounts = accounts,
        methods = methods,
        events = proposalNamespace.events
    )
}

fun Sign.Model.SessionProposal.network(): String? {
    val chains = requiredNamespaces[nameTag()]?.chains
    val reference = chains?.firstOrNull { it.contains(nameTag()) }
    return reference?.split(":")?.get(1)
}

fun String.toNetwork(): String? {
    val list = split(":")
    return if (list.size > 1) {
        list[1]
    } else {
        null
    }
}

private fun nameTag(): String {
    return "flow"
}

fun Sign.Model.SessionProposal.reject() {
    val rejectionReason = "Reject Session"
    val reject = Sign.Params.Reject(
        proposerPublicKey = proposerPublicKey,
        reason = rejectionReason,
    )

    SignClient.rejectSession(reject) { error -> loge(error.throwable) }
}

internal fun WCRequest.approve(result: String) {
    logd(TAG, "SessionRequest.approve:$result")
    val response = Sign.Params.Response(
        sessionTopic = topic,
        jsonRpcResponse = Sign.Model.JsonRpcResponse.JsonRpcResult(requestId, result)
    )
    SignClient.respond(response) { error -> loge(error.throwable) }
}

internal fun WCRequest.reject() {
    SignClient.respond(
        Sign.Params.Response(
            sessionTopic = topic,
            jsonRpcResponse = Sign.Model.JsonRpcResponse.JsonRpcError(requestId, 0, "User rejected")
        )
    ) { error -> loge(error.throwable) }
}

internal class SignableMessage(
    @SerializedName("addr")
    val addr: String?,
    @SerializedName("message")
    val message: String?,
)
