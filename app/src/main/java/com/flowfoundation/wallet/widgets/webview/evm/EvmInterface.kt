package com.flowfoundation.wallet.widgets.webview.evm

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import com.flowfoundation.wallet.manager.evm.DAppMethod
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.evm.Numeric
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.evm.dialog.EvmRequestAccountDialog
import com.flowfoundation.wallet.widgets.webview.evm.model.EvmDialogModel
import com.flowfoundation.wallet.widgets.webview.fcl.dialog.FclSignMessageDialog
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel
import com.nftco.flow.sdk.DomainTag
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.bytesToHex
import org.json.JSONObject
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.rlp.RlpString
import org.web3j.rlp.RlpType
import splitties.alertdialog.appcompat.message
import splitties.alertdialog.appcompat.okButton
import splitties.alertdialog.appcompat.title
import splitties.alertdialog.material.materialAlertDialog
import wallet.core.jni.Hash


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
            DAppMethod.SIGN_TYPED_MESSAGE -> {
                val data = extractMessage(obj)
                val raw = extractRaw(obj)
//                handleSignTypedMessage(id, data, raw)
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

    private fun signEthereumMessage(message: String): String {
        val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return ""
        val address = WalletManager.wallet()?.walletAddress() ?: return ""
        val flowAddress = FlowAddress(address)
        val keyIndex = flowAddress.currentKeyId(cryptoProvider.getPublicKey())

        val hashedData = hashPersonalMessage(message.toByteArray())
        val signableData = DomainTag.USER_DOMAIN_TAG + hashedData
        val sign = cryptoProvider.getSigner().sign(signableData)
        val rlpList = RlpList(asRlpValues(keyIndex, flowAddress.bytes, "evm", sign))
        val encoded = RlpEncoder.encode(rlpList)

        logd("evm", "hashedData:::${hashedData.bytesToHex()}")
        logd("evm", "signableData:::${signableData.bytesToHex()}")
        logd("evm", "sign:::${sign.bytesToHex()}")
        logd("evm", "encoded:::${encoded.bytesToHex()}")
        logd("evm", "signResult:::${Numeric.toHexString(encoded)}")

        return Numeric.toHexString(encoded)
    }

    private fun asRlpValues(keyIndex: Int, address: ByteArray, capabilityPath: String, signature: ByteArray): List<RlpType> {
        return listOf(
            RlpList(RlpString.create(keyIndex.toBigInteger())),
            RlpString.create(address),
            RlpString.create(capabilityPath),
            RlpList(RlpString.create(signature)))
    }

    private fun hashPersonalMessage(message: ByteArray): ByteArray {
        val messagePrefix = "\u0019Ethereum Signed Message:\n"
        val prefix = (messagePrefix + message.size).toByteArray()
        val result = ByteArray(prefix.size + message.size)
        System.arraycopy(prefix, 0, result, 0, prefix.size)
        System.arraycopy(message, 0, result, prefix.size, message.size)
        return Hash.keccak256(result)
    }

}