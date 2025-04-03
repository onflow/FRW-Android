package com.flowfoundation.wallet.widgets.webview

import android.webkit.JavascriptInterface
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.page.browser.widgets.LilicoWebView
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.fcl.FclMessageHandler
import com.flowfoundation.wallet.widgets.webview.fcl.authzTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nftco.flow.sdk.FlowTransactionStatus

class JsInterface(
    private val webView: LilicoWebView,
) {

    private val messageHandler by lazy { FclMessageHandler(webView) }

    @JavascriptInterface
    fun message(data: String) {
        messageHandler.onHandleMessage(data)
    }

    @JavascriptInterface
    fun transaction(json: String) {
        logd(TAG, "transaction: $json")
        ioScope {
            val authzTransaction = authzTransaction() ?: return@ioScope
            val tid = Gson().fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)["txId"] as String
            val transactionState = TransactionState(
                transactionId = tid,
                time = System.currentTimeMillis(),
                state = FlowTransactionStatus.UNKNOWN.num,
                type = TransactionState.TYPE_FCL_TRANSACTION,
                data = Gson().toJson(authzTransaction),
            )
            uiScope {
                if (TransactionStateManager.getTransactionStateById(tid) != null) return@uiScope
                TransactionStateManager.newTransaction(transactionState)
                pushBubbleStack(transactionState)
            }
        }
    }

    companion object {
        private val TAG = JsInterface::class.java.simpleName
    }
}