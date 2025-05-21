package com.flowfoundation.wallet.widgets.webview.fcl

import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.flowjvm.transaction.PayerSignable
import com.flowfoundation.wallet.manager.flowjvm.transaction.SignPayerResponse
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallet.walletAddress
import com.flowfoundation.wallet.network.functions.FUNCTION_SIGN_AS_PAYER
import com.flowfoundation.wallet.network.functions.executeHttpFunction
import com.flowfoundation.wallet.page.browser.widgets.LilicoWebView
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logv
import com.flowfoundation.wallet.utils.safeRun
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.fcl.dialog.FclAuthnDialog
import com.flowfoundation.wallet.widgets.webview.fcl.dialog.FclSignMessageDialog
import com.flowfoundation.wallet.widgets.webview.fcl.dialog.authz.FclAuthzDialog
import com.flowfoundation.wallet.widgets.webview.fcl.dialog.checkAndShowNetworkWrongDialog
import com.flowfoundation.wallet.widgets.webview.fcl.model.AuthzTransaction
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclAuthnResponse
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclAuthzResponse
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclResponse
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclService
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclSignMessageResponse
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclSimpleResponse
import com.flowfoundation.wallet.widgets.webview.fcl.model.toAuthzTransaction
import com.ionspin.kotlin.bignum.integer.toBigInteger
import org.onflow.flow.infrastructure.Cadence
import java.lang.reflect.Type
import com.flowfoundation.wallet.manager.account.AccountManager

private val TAG = FclMessageHandler::class.java.simpleName

private var authzTransaction: AuthzTransaction? = null

fun authzTransaction() = authzTransaction

class FclMessageHandler(
    private val webView: LilicoWebView,
) {
    private fun activity() = findActivity(webView) as FragmentActivity

    private fun wallet(): String {
        // Try getting from WalletManager first
        val walletAddress = WalletManager.wallet()?.walletAddress().orEmpty()
        if (walletAddress.isNotBlank()) {
            logd(TAG, "Got wallet address from WalletManager: '$walletAddress'")
            return walletAddress
        }
        
        // If empty, try from AccountManager
        val account = AccountManager.get()
        val accountWalletAddress = account?.wallet?.walletAddress()
        if (!accountWalletAddress.isNullOrBlank()) {
            logd(TAG, "Got wallet address from AccountManager: '$accountWalletAddress'")
            return accountWalletAddress
        }
        
        // If still empty, try getting from current account directly
        val selectedAddress = WalletManager.selectedWalletAddress()
        if (selectedAddress.isNotBlank()) {
            logd(TAG, "Got wallet address from WalletManager.selectedWalletAddress(): '$selectedAddress'")
            return selectedAddress
        }
        
        logd(TAG, "Could not find any wallet address")
        return ""
    }

    private var message: String = ""

    private var service: String = ""

    private var fclResponse: FclResponse? = null

    private var readyToSignEnvelope = false

    fun onHandleMessage(message: String) {
        ioScope { dispatch(message) }
    }

    private fun dispatch(message: String) {
        if (message.isBlank() || message == this.message) {
            return
        }

        if (wallet().isBlank()) {
            toast(msgRes = R.string.not_logged_in_toast)
            return
        }

        this.message = message
        logv(TAG, "message:$message")

        val basicJson = message.fromJson<Map<String, Any>>(object : TypeToken<Map<String, Any>>() {}.type) ?: return

        if (basicJson.isService()) {
            dispatchServiceResponse(message)
        } else if (basicJson["type"] as? String == TYPE_VIEW_RESPONSE) {
            uiScope { dispatchViewReadyResponse(message) }
        }
//        val parsedMessage = parseMessage(message) ?: return
//
//        if (parsedMessage.isService()) {
//            dispatchServiceResponse(message)
//        } else if (parsedMessage.type == TYPE_VIEW_RESPONSE) {
//            uiScope { dispatchViewReadyResponse(message) }
//        }
    }

    private suspend fun dispatchViewReadyResponse(message: String) {
        val service = this.service
        val fcl = message.fromJson(FclSimpleResponse::class.java) ?: return
        if (service != fcl.serviceType()) {
            logd(TAG, "service not same (old:$service, new:${fcl.service})")
            return
        }

        when (fcl.service.type) {
            "authn" -> dispatchAuthn(message.fromJson(FclAuthnResponse::class.java)!!)
            "authz" -> dispatchAuthz(message.fromJson(FclAuthzResponse::class.java)!!)
            "user-signature" -> dispatchSignMessage(message.fromJson(FclSignMessageResponse::class.java)!!)
        }
    }

    private fun dispatchServiceResponse(message: String) {
        message.fromJson(FclService::class.java)?.let {
            service = it.service.type
            fclResponse = null
            if (service == "pre-authz") {
                webView.postPreAuthzResponse()
            } else webView.postMessage("{type: '$TYPE_VIEW_READY'}")
        }
    }

    private suspend fun dispatchAuthn(fcl: FclAuthnResponse) {
        if (webView.isLoading) {
            toast(msgRes = R.string.wait_website_fully_loaded)
            return
        }
        if (fcl.isDispatching()) {
            return
        }
        logd(TAG, "dispatchAuthn")
        fclResponse = fcl
        val approve = FclAuthnDialog().show(
            activity().supportFragmentManager,
            FclDialogModel(title = webView.title, url = webView.url, logo = fcl.config?.app?.icon, network = fcl.config?.client?.network)
        )
        if (approve) {
            wallet().let { webView.postAuthnViewReadyResponse(fcl, it) }
        }
        finishService()
    }

    private fun dispatchAuthz(fcl: FclAuthzResponse) {
        if (fcl.isDispatching()) {
            logd(TAG, "fcl isDispatching:${fcl.uniqueId()}")
            return
        }
        fclResponse = fcl

        if (fcl.body.fType == "Signable") {
            logd(TAG, "roles:${fcl.body.roles}")
        }

        if (fcl.isSignAuthz()) {
            logd(TAG, "signAuthz")
            signAuthz(fcl)
        } else if (fcl.isSignPayload() && !FclAuthzDialog.isShowing()) {
            logd(TAG, "signPayload")
            signPayload(fcl)
        } else if ((readyToSignEnvelope && FclAuthzDialog.isShowing() && fcl.isSignEnvelope())) {
            logd(TAG, "fclSignEnvelope")
            ioScope { signEnvelope(fcl, webView) { FclAuthzDialog.dismiss() } }
        }
    }

    private fun dispatchSignMessage(fcl: FclSignMessageResponse) {
        if (fcl.isDispatching()) {
            logd(TAG, "fcl isDispatching:${fcl.uniqueId()}")
            return
        }
        fclResponse = fcl

        logd(TAG, "dispatchSignMessage:${fcl.uniqueId()}")

        val data = FclDialogModel(
            signMessage = fcl.body?.message,
            url = webView.url,
            title = webView.title,
            logo = fcl.config?.app?.icon,
            network = fcl.config?.client?.network
        )
        if (checkAndShowNetworkWrongDialog(activity().supportFragmentManager, data)) {
            finishService()
            return
        }

        FclSignMessageDialog.show(
            activity().supportFragmentManager,
            data
        )
        FclSignMessageDialog.observe { approve ->
            if (approve) {
                webView.postSignMessageResponse(fcl)
            }
            finishService()
        }
    }

    private fun signAuthz(fcl: FclAuthzResponse) {
        val data = fcl.toFclDialogModel(webView)

        if (checkAndShowNetworkWrongDialog(activity().supportFragmentManager, data)) {
            finishService()
            return
        }

        FclAuthzDialog.show(
            activity().supportFragmentManager,
            data,
        )
        FclAuthzDialog.observe { approve ->
            if (approve) {
                uiScope { authzTransaction = fcl.toAuthzTransaction(webView) }
                FclAuthzDialog.dismiss()
                webView.postAuthzPayloadSignResponse(fcl)
            }
            finishService()
        }
    }

    private fun signPayload(fcl: FclAuthzResponse) {
        val data = fcl.toFclDialogModel(webView)
        if (checkAndShowNetworkWrongDialog(activity().supportFragmentManager, data)) {
            finishService()
            return
        }
        FclAuthzDialog.show(
            activity().supportFragmentManager,
            data,
        )
        FclAuthzDialog.observe { approve ->
            readyToSignEnvelope = approve
            if (approve) {
                webView.postAuthzPayloadSignResponse(fcl)
            } else {
                finishService()
            }
        }
    }

    private suspend fun signEnvelope(fcl: FclAuthzResponse, webView: WebView, callback: () -> Unit) {
        val voucher = fcl.body.voucher
        val transaction = org.onflow.flow.models.Transaction(
            script = voucher.cadence ?: "",
            arguments = voucher.arguments?.map { Cadence.string(it.toString()) } ?: emptyList(),
            referenceBlockId = voucher.refBlock ?: "",
            gasLimit = (voucher.computeLimit ?: 9999).toBigInteger(),
            payer = voucher.payer ?: "",
            proposalKey = org.onflow.flow.models.ProposalKey(
                address = voucher.proposalKey.address ?: "",
                keyIndex = voucher.proposalKey.keyId ?: 0,
                sequenceNumber = (voucher.proposalKey.sequenceNum ?: 0).toBigInteger()
            ),
            authorizers = voucher.authorizers ?: emptyList()
        )

        val response = executeHttpFunction(
            FUNCTION_SIGN_AS_PAYER, PayerSignable(
                transaction = transaction,
                message = PayerSignable.Message(fcl.body.message)
            )
        )

        safeRun {
            val sign = Gson().fromJson(response, SignPayerResponse::class.java).envelopeSigs

            webView.postAuthzEnvelopeSignResponse(sign)
            uiScope { authzTransaction = fcl.toAuthzTransaction(webView) }

            callback.invoke()
        }
        readyToSignEnvelope = false
        finishService()
    }

    private fun FclResponse.isDispatching(): Boolean = this.uniqueId() == fclResponse?.uniqueId()

    private fun finishService() {
        service = ""
        fclResponse = null
    }
}

private fun Map<String, Any>.isService(): Boolean {
    return this["type"] == null && ((this["service"] as? Map<*, *>)?.get("type") != null || (this["service"] as? Map<*, *>)?.get("f_type") == "Service")
}

private fun <T> String.fromJson(clz: Class<T>): T? {
    return try {
        Gson().fromJson(this, clz)
    } catch (e: Exception) {
        loge(e)
        null
    }
}

private fun <T> String.fromJson(typeOfT: Type): T? {
    return try {
        Gson().fromJson<T>(this, typeOfT)
    } catch (e: Exception) {
        loge(e)
        null
    }
}

private fun FclAuthzResponse.isSignEnvelope(): Boolean {
    return service.type == "authz" && body.fType == "Signable" && body.roles.isSignEnvelope()
}

private fun FclAuthzResponse.isSignAuthz(): Boolean {
    return service.type == "authz" && body.fType == "Signable" && body.roles.isSignAuthz()
}

private fun FclAuthzResponse.isSignPayload(): Boolean {
    return service.type == "authz" && body.fType == "Signable" && body.roles.isSignPayload()
}

private fun FclAuthzResponse.Body.Roles.isSignEnvelope(): Boolean {
    return payer && !authorizer && !proposer
}

private fun FclAuthzResponse.Body.Roles.isSignAuthz(): Boolean {
    return payer && authorizer && proposer
}

private fun FclAuthzResponse.Body.Roles.isSignPayload(): Boolean {
    return !payer && authorizer && proposer
}

