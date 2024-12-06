package com.flowfoundation.wallet.widgets.webview.evm

import android.webkit.JavascriptInterface
import androidx.fragment.app.FragmentActivity
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.app.MAINNET_CHAIN_ID
import com.flowfoundation.wallet.manager.app.TESTNET_CHAIN_ID
import com.flowfoundation.wallet.manager.app.networkStringByChainId
import com.flowfoundation.wallet.manager.evm.DAppMethod
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.evm.sendEthereumTransaction
import com.flowfoundation.wallet.manager.evm.signEthereumMessage
import com.flowfoundation.wallet.manager.evm.signTypedData
import com.flowfoundation.wallet.manager.flowjvm.Cadence
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.page.browser.widgets.LilicoWebView
import com.flowfoundation.wallet.page.evm.EnableEVMDialog
import com.flowfoundation.wallet.page.token.custom.widget.AddCustomTokenDialog
import com.flowfoundation.wallet.page.wallet.dialog.MoveDialog
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.isShowMoveDialog
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EVMSendTransactionDialog
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EvmRequestAccountDialog
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMDialogModel
import com.flowfoundation.wallet.widgets.webview.evm.model.EvmTransaction
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EVMSignMessageDialog
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EVMSignTypedDataDialog
import com.flowfoundation.wallet.widgets.webview.fcl.dialog.checkAndShowNetworkWrongDialog
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel
import com.google.gson.Gson
import com.nftco.flow.sdk.bytesToHex
import org.json.JSONObject
import org.web3j.utils.Numeric


class EvmInterface(
    private val webView: LilicoWebView
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
                if (webView.isLoading) {
                    toast(msgRes = R.string.wait_website_fully_loaded)
                    return
                }
                uiScope {
                    if (EVMWalletManager.haveEVMAddress()) {
                        if (isShowMoveDialog()) {
                            MoveDialog().showMove(activity().supportFragmentManager, webView.title)
                        }
                        val connect = EvmRequestAccountDialog().show(
                            activity().supportFragmentManager,
                            EVMDialogModel(
                                title = webView.title,
                                url = webView.url,
                                network = network
                            )
                        )
                        if (connect) {
                            val address = EVMWalletManager.getEVMAddress()
                            webView.setAddress(network, address.orEmpty(), id)
                        }
                    } else {
                        EnableEVMDialog.show(activity().supportFragmentManager)
                    }
                }
            }
            DAppMethod.SWITCH_ETHEREUM_CHAIN -> {
                uiScope {
                    when (val rpcChainId = extractRPCChainId(obj)) {
                        MAINNET_CHAIN_ID, TESTNET_CHAIN_ID -> {
                            if (checkAndShowNetworkWrongDialog(activity().supportFragmentManager,
                                FclDialogModel(
                                    title = webView.title,
                                    url = webView.url,
                                    network = networkStringByChainId(rpcChainId)
                                )
                            )) {
                                logd(TAG, "switch network to::${networkStringByChainId(rpcChainId)}")
                                return@uiScope
                            }
                            logd(TAG, "no need to switch")
                            webView.sendNull(network, id)
                        }
                        else -> {
                            logd(TAG, "Unsupported ChainId::$rpcChainId")
                            val message = activity().getString(R.string.unsupported_chain_id, rpcChainId)
                            toast(msg = message)
                            webView.sendError(network, message, id)
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
                    logd(TAG, "transaction obj::$obj")
                    val transaction = Gson().fromJson(obj.optString("object"), EvmTransaction::class.java)
                    logd(TAG, "transaction::$transaction")
                    uiScope {
                        handleTransaction(transaction, id, network)
                    }
                }
            }
            DAppMethod.SIGN_TYPED_MESSAGE -> {
                val data = extractMessage(obj)
                val raw = extractRaw(obj)
                logd(TAG, "signTypedMessage obj::$obj")
                logd(TAG, "signTypedMessage data::$data")
                logd(TAG, "signTypedMessage raw::$raw")

                uiScope {
                    handleSignTypedMessage(id, data, raw, network)
                }
            }
            DAppMethod.WATCH_ASSET -> {
                logd(TAG, "watchAsset obj::$obj")
                val contractAddress = extractContractAddress(obj)
                uiScope {
                    AddCustomTokenDialog.show(
                        activity().supportFragmentManager,
                        contractAddress
                    )
                    AddCustomTokenDialog.observe { approve ->
                        webView.sendResult(network, approve.toString(), id)
                    }
                }
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
            cadence = Cadence.CADENCE_CALL_EVM_CONTRACT.getScript()
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

    private fun extractRPCChainId(json: JSONObject): Int {
        val param = json.getJSONObject("object")
        val chainId = param.getString("chainId")
        return chainId.removePrefix("0x").toInt(radix = 16)
    }

    private fun extractMessage(json: JSONObject): ByteArray {
        val param = json.getJSONObject("object")
        val data = param.getString("data")
        return Numeric.hexStringToByteArray(data)
    }

    private fun extractContractAddress(json: JSONObject): String {
        val param = json.getJSONObject("object")
        val contract = param.getString("contract")
        return contract
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
        EVMSignMessageDialog.show(
            activity().supportFragmentManager,
            model
        )
        EVMSignMessageDialog.observe { approve ->
            if (approve) {
                webView.sendResult(network, signEthereumMessage(signMessage), id)
            }
        }
    }

    private fun handleSignTypedMessage(id: Long, data: ByteArray, raw: String, network: String) {
        val model = FclDialogModel(
            signMessage = raw,
            url = webView.url,
            title = webView.title,
            logo = webView.url?.toFavIcon(),
            network = network
        )
        EVMSignTypedDataDialog.show(
            activity().supportFragmentManager,
            model
        )
        EVMSignTypedDataDialog.observe { approve ->
            if (approve) {
                webView.sendResult(network, signTypedData(data), id)
            }
        }
    }

}