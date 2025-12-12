package com.flowfoundation.wallet.manager.walletconnect

import androidx.appcompat.app.AppCompatActivity
import com.flowfoundation.wallet.manager.app.EVM_MAINNET
import com.flowfoundation.wallet.manager.app.EVM_TESTNET
import com.flowfoundation.wallet.manager.evm.DAppEVMConnectionManager
import com.flowfoundation.wallet.manager.wallet.WalletManager

import com.flowfoundation.wallet.manager.walletconnect.model.WCRequest
import com.flowfoundation.wallet.manager.walletconnect.model.WalletConnectMethod
import com.flowfoundation.wallet.utils.extensions.openInSystemBrowser
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.uiScope
import com.google.gson.annotations.SerializedName
import com.reown.sign.client.Sign
import com.reown.sign.client.SignClient

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

    SignClient.approveSession(
        Sign.Params.Approve(
            proposerPublicKey = proposerPublicKey,
            namespaces = namespaces
        )
    ) { error ->
        loge(error.throwable)
    }
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
            val evmAddress = DAppEVMConnectionManager.getCurrentAccount()?.address.orEmpty()
            if (evmAddress.isNotEmpty()) {
                "$it:${evmAddress}"
            } else {
                null
            }
        }.toList()
    } else {
        chains.map { "$it:${WalletManager.getCurrentFlowWalletAddress().orEmpty()}" }.toList()
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

internal fun WCRequest.approve(result: String, onSuccess: (() -> Unit)? = null) {
    logd(TAG, "SessionRequest.approve:$result")
    val response = Sign.Params.Response(
        sessionTopic = topic,
        jsonRpcResponse = Sign.Model.JsonRpcResponse.JsonRpcResult(requestId, result)
    )
    SignClient.respond(
        response,
        onSuccess = { onSuccess?.invoke() },
        onError = { error -> loge(error.throwable) }
    )
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

internal fun WCRequest.findRedirectUrl(): String? {
    return try {
        val sessionRedirect = SignClient.getActiveSessionByTopic(topic)?.redirect
        when {
            !sessionRedirect.isNullOrBlank() -> sessionRedirect
            !metaData?.redirect.isNullOrBlank() -> metaData?.redirect
            else -> null
        }
    } catch (e: Exception) {
        loge(TAG, "Error finding redirect URL: ${e.message}")
        null
    }
}

internal fun WCRequest.handleRedirectIfNeeded(redirectUrl: String?, activity: AppCompatActivity?) {
    if (redirectUrl.isNullOrBlank()) {
        logd(TAG, "No redirect URL configured for topic: $topic")
        return
    }

    if (activity == null) {
        loge(TAG, "No activity available to perform redirect")
        return
    }

    uiScope {
        try {
            logd(TAG, "Redirecting to: $redirectUrl")
            redirectUrl.openInSystemBrowser(activity, true)
        } catch (e: Exception) {
            loge(TAG, "Failed to open redirect URL: ${e.message}")
            loge(e)
        }
    }
}
