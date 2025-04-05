package com.flowfoundation.wallet.widgets.webview

import android.graphics.Color
import android.webkit.JavascriptInterface
import androidx.core.graphics.toColorInt
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.page.browser.widgets.LilicoWebView
import com.flowfoundation.wallet.page.window.bubble.tools.pushBubbleStack
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.fcl.FclMessageHandler
import com.flowfoundation.wallet.widgets.webview.fcl.authzTransaction
import org.onflow.flow.models.TransactionStatus

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
                state = TransactionStatus.UNKNOWN.ordinal,
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

    @JavascriptInterface
    fun windowColor(color: String) {
        logd(TAG, "window color:$color")
        val colorInt = color.toColorInt()
        webView.onWindowColorChange(if (colorInt == Color.BLACK) R.color.deep_bg.res2color() else colorInt)
    }

    companion object {
        private val TAG = JsInterface::class.java.simpleName
    }
}