package com.flowfoundation.wallet.page.component.deeplinking

import android.content.Context
import android.net.Uri
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.networkId
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.SendAmountActivity
import com.flowfoundation.wallet.page.wallet.dialog.SwapDialog
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val TAG = "DeepLinkingDispatch"

enum class DeepLinkPath(val path: String) {
    DAPP("/dapp"),
    SEND("/send"),
    BUY("/buy");

    companion object {
        fun fromPath(path: String?): DeepLinkPath? {
            return entries.firstOrNull { it.path == path }
        }
    }
}


fun dispatchDeepLinking(context: Context, uri: Uri) {
    ioScope {
        val wcUri = getWalletConnectUri(uri)
        if (wcUri?.startsWith("wc:") == true) {
            dispatchWalletConnect(uri)
            return@ioScope
        }
        PendingActionHelper.savePendingDeepLink(context, uri)
    }
}

fun executePendingDeepLink(uri: Uri) {
    if (uri.host == "link.wallet.flow.com") {
        when (DeepLinkPath.fromPath(uri.path)) {
            DeepLinkPath.DAPP -> {
                val dappUrl = uri.getQueryParameter("url")
                if (dappUrl != null) {
                    dispatchDapp(dappUrl)
                }
            }
            DeepLinkPath.SEND -> {
                val recipient = uri.getQueryParameter("recipient")
                val network = uri.getQueryParameter("network")
                val value = uri.getQueryParameter("value")
                if (recipient != null) {
                    dispatchSend(uri, recipient, network, parseValue(value))
                }
            }
            DeepLinkPath.BUY -> {
                dispatchBuy()
            }
            else -> {
                logd(TAG, "executeDeepLinking: unknown path=${uri.path}")
            }
        }
    }
}

// https://lilico.app/?uri=wc%3A83ba9cb3adf9da4b573ae0c499d49be91995aa3e38b5d9a41649adfaf986040c%402%3Frelay-protocol%3Diridium%26symKey%3D618e22482db56c3dda38b52f7bfca9515cc307f413694c1d6d91931bbe00ae90
// wc:83ba9cb3adf9da4b573ae0c499d49be91995aa3e38b5d9a41649adfaf986040c@2?relay-protocol=iridium&symKey=618e22482db56c3dda38b52f7bfca9515cc307f413694c1d6d91931bbe00ae90
private fun dispatchWalletConnect(uri: Uri): Boolean {
    return runCatching {
        val data = getWalletConnectUri(uri)
        logd(TAG, "dispatchWalletConnect: $data")
        assert(data?.startsWith("wc:") ?: false)

        if (!WalletConnect.isInitialized()) {
            runBlocking {
                while (!WalletConnect.isInitialized()) {
                    delay(200)
                }
                WalletConnect.get().pair(data.toString())
            }
        } else {
            WalletConnect.get().pair(data.toString())
        }
    }.getOrNull() != null
}

fun getWalletConnectUri(uri: Uri): String? {
    return runCatching {
        val uriString = uri.toString()

        val uriParamStart = uriString.indexOf("uri=")
        val wcUriEncoded = if (uriParamStart != -1) {
            uriString.substring(uriParamStart + 4)
        } else {
            uri.getQueryParameter("uri")
        }
        wcUriEncoded?.let {
            if (it.contains("%")) {
                URLDecoder.decode(it, StandardCharsets.UTF_8.name())
            } else {
                it
            }
        }
    }.getOrNull()
}

private fun parseValue(value: String?): BigDecimal? {
    if (value == null) return null

    return try {
        if (value.startsWith("0x", ignoreCase = true)) {
            val amountValue = Numeric.decodeQuantity(value)
            Convert.fromWei(amountValue.toString(), Convert.Unit.ETHER)
        } else {
            BigDecimal(value)
        }
    } catch (e: Exception) {
        logd(TAG, "Failed to parse value: $value, ${e.message}")
        null
    }
}

private fun dispatchDapp(dappUrl: String) {
    BaseActivity.getCurrentActivity()?.let {
        uiScope {
            openBrowser(it, dappUrl)
            return@uiScope
        }
    }
}

private fun dispatchSend(uri: Uri, recipient: String, network: String?, value: BigDecimal?) {
    logd(TAG, "dispatchSend: recipient=$recipient, network=$network, value=$value")
    BaseActivity.getCurrentActivity()?.let {
        if (network != null && network != chainNetWorkString()) {
            PendingActionHelper.savePendingDeepLink(it, uri)
            uiScope {
                SwitchNetworkDialog(
                    context = it,
                    dialogType = DialogType.DEEPLINK,
                    targetNetwork = networkId(network)
                ).show()
            }
        } else {
            SendAmountActivity.launch(
                it,
                AddressBookContact(address = recipient.toAddress()),
                FlowCoinListManager.getFlowCoinContractId(),
                value?.toString()
            )
        }
    }
}

private fun dispatchBuy() {
    BaseActivity.getCurrentActivity()?.let {
        uiScope {
            SwapDialog.show(it.supportFragmentManager)
        }
    }
}