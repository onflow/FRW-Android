package com.flowfoundation.wallet.widgets.webview.evm

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import com.flowfoundation.wallet.manager.evm.DAppMethod
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.evm.sendEthereumTransaction
import com.flowfoundation.wallet.manager.evm.signEthereumMessage
import com.flowfoundation.wallet.manager.flowjvm.CADENCE_CALL_EVM_CONTRACT
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EVMSendTransactionDialog
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EvmRequestAccountDialog
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMDialogModel
import com.flowfoundation.wallet.widgets.webview.evm.model.EvmTransaction
import com.flowfoundation.wallet.widgets.webview.fcl.dialog.FclSignMessageDialog
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel
import com.google.gson.Gson
import com.nftco.flow.sdk.bytesToHex
import org.json.JSONObject
import org.web3j.utils.Numeric


class EvmInterface(
    private val webView: WebView
) {
    companion object {
        const val ETH_NETWORK = "ethereum"
        const val TAG = "EVMInterface"
    }
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
                        EVMDialogModel(title = webView.title, url = webView.url, network = network)
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
                if (network == ETH_NETWORK)
                    uiScope {
                        handleSignMessage(id, data, network)
                    }
            }
            DAppMethod.SIGN_PERSONAL_MESSAGE -> {
                val data = extractMessage(obj)
                uiScope {
                    handleSignMessage(id, data, network)
                }
            }
            DAppMethod.SIGN_TRANSACTION -> {
                if (network == ETH_NETWORK) {
                    val transaction = Gson().fromJson(obj.optString("object"), EvmTransaction::class.java)
                    uiScope {
                        handleTransaction(transaction, id, network)
                    }
                }
            }
            DAppMethod.SIGN_TYPED_MESSAGE -> {
                val data = extractMessage(obj)
                val raw = extractRaw(obj)
//                handleSignTypedMessage(id, data, raw)
            }
            else -> {
                logd("evm", "methodNotImplement:::$method")
            }
        }
    }

    private fun handleTransaction(transaction: EvmTransaction, id: Long, network: String) {
        val model = EVMDialogModel(
            url = webView.url,
            title = webView.title,
            logo = webView.url?.toFavIcon(),
            network = network,
            cadence = CADENCE_CALL_EVM_CONTRACT
        )
        EVMSendTransactionDialog.show(
            activity().supportFragmentManager,
            model
        )
        EVMSendTransactionDialog.observe { isApprove ->
            if (isApprove) {
                sendEthereumTransaction(transaction) { txHash ->
                    webView.sendResult(network, txHash, id)
                }
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

    private fun handleSignMessage(id: Long, data: ByteArray, network: String) {
        val signMessage = String(data, Charsets.UTF_8)
        val model = FclDialogModel(
            signMessage = data.bytesToHex(),
            url = webView.url,
            title = webView.title,
            logo = webView.url?.toFavIcon(),
            network = network
        )
        FclSignMessageDialog.show(
            activity().supportFragmentManager,
            model
        )
        FclSignMessageDialog.observe { approve ->
            if (approve) {
                webView.sendResult(network, signEthereumMessage(signMessage), id)
            }
        }
    }

}