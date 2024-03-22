package com.flowfoundation.wallet.widgets.webview.evm

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import com.flowfoundation.wallet.manager.evm.DAppMethod
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.evm.Numeric
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EvmRequestAccountDialog
import com.flowfoundation.wallet.widgets.webview.evm.model.EvmDialogModel
import org.json.JSONObject
import splitties.alertdialog.appcompat.cancelButton
import splitties.alertdialog.appcompat.message
import splitties.alertdialog.appcompat.okButton
import splitties.alertdialog.appcompat.title
import splitties.alertdialog.material.materialAlertDialog


class EvmInterface(
    private val webView: WebView
) {
    private fun activity() = findActivity(webView) as FragmentActivity

    @JavascriptInterface
    fun postMessage(json: String) {
        val obj = JSONObject(json)
        val id = obj.getLong("id")
        val method = DAppMethod.fromValue(obj.getString("name"))
        val network = obj.getString("network")
        when (method) {
            DAppMethod.REQUEST_ACCOUNTS -> {
                uiScope {
                    val connect = EvmRequestAccountDialog().show(
                        activity().supportFragmentManager,
                        EvmDialogModel(title = webView.title, url = webView.url, network = network)
                    )
                    if (connect) {
                        val address = EVMWalletManager.getEVMAddress()
                        val setAddress = "window.$network.setAddress(\"$address\");"
                        val callback = "window.$network.sendResponse($id, [\"$address\"])"
                        webView.post {
                            webView.evaluateJavascript(setAddress) {
                                // ignore
                            }
                            webView.evaluateJavascript(callback) { value ->
                                println(value)
                            }
                        }
                    }
                }
            }
            DAppMethod.SIGN_MESSAGE -> {
                val data = extractMessage(obj)
                if (network == "ethereum")
                    handleSignMessage(id, data, addPrefix = false)
            }
            DAppMethod.SIGN_PERSONAL_MESSAGE -> {
                val data = extractMessage(obj)
                handleSignMessage(id, data, addPrefix = true)
            }
            DAppMethod.SIGN_TYPED_MESSAGE -> {
                val data = extractMessage(obj)
                val raw = extractRaw(obj)
                handleSignTypedMessage(id, data, raw)
            }
            else -> {
                webView.context.materialAlertDialog {
                    title = "Error"
                    message = "$method not implemented"
                    okButton {
                    }
                }.show()
            }
        }
    }

    private fun extractMessage(json: JSONObject): ByteArray {
        val param = json.getJSONObject("object")
        val data = param.getString("data")
        return Numeric.hexStringToByteArray(data)
    }

    private fun extractRaw(json: JSONObject): String {
        val param = json.getJSONObject("object")
        return param.getString("raw")
    }

    private fun handleSignMessage(id: Long, data: ByteArray, addPrefix: Boolean) {
        webView.context.materialAlertDialog {
            title = "Sign Ethereum Message"
            message = if (addPrefix) String(data, Charsets.UTF_8) else Numeric.toHexString(data)
            cancelButton {
                webView.sendError("ethereum","Cancel", id)
            }
            okButton {
                webView.sendResult("ethereum", signEthereumMessage(data, addPrefix), id)
            }
        }.show()
    }


    private fun handleSignTypedMessage(id: Long, data: ByteArray, raw: String) {
        webView.context.materialAlertDialog {
            title = "Sign Typed Message"
            message = raw
            cancelButton {
                webView.sendError("ethereum","Cancel", id)
            }
            okButton {
                webView.sendResult("ethereum", signEthereumMessage(data, false), id)
            }
        }.show()
    }

    private fun signEthereumMessage(message: ByteArray, addPrefix: Boolean): String {
        var data = message
        if (addPrefix) {
            val messagePrefix = "\u0019Ethereum Signed Message:\n"
            val prefix = (messagePrefix + message.size).toByteArray()
            val result = ByteArray(prefix.size + message.size)
            System.arraycopy(prefix, 0, result, 0, prefix.size)
            System.arraycopy(message, 0, result, prefix.size, message.size)
            data = wallet.core.jni.Hash.keccak256(result)
        }

//        val signatureData = privateKey.sign(data, Curve.SECP256K1)
//            .apply {
//                (this[this.size - 1]) = (this[this.size - 1] + 27).toByte()
//            }
        return Numeric.toHexString(EVMWalletManager.signData(data))
    }
}