package com.flowfoundation.wallet.page.component.deeplinking

import android.content.Context
import android.net.Uri
import android.util.Log
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.firebase.auth.isUserSignIn
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.networkId
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.SendAmountActivity
import com.flowfoundation.wallet.page.wallet.dialog.SwapDialog
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isRegistered
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

suspend fun dispatchDeepLinking(context: Context, uri: Uri) {
    logd(TAG, "dispatchDeepLinking: Processing URI: $uri")
    val wcUri = getWalletConnectUri(uri)
    if (wcUri?.startsWith("wc:") == true) {
        val success = dispatchWalletConnect(uri)
        if (success) {
            logd(TAG, "WalletConnect dispatch completed successfully")
        } else {
            loge(TAG, "WalletConnect dispatch failed")
            // Save as pending action if WalletConnect fails
            PendingActionHelper.savePendingDeepLink(context, uri)
        }
        return
    }
    logd(TAG, "No WalletConnect URI found, saving as pending action")
    PendingActionHelper.savePendingDeepLink(context, uri)
}

suspend fun executePendingDeepLink(uri: Uri) {
    if (isRegistered() && isUserSignIn()) {
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
    } else {
        toast(R.string.deeplink_login_failed)
    }

}

// https://lilico.app/?uri=wc%3A83ba9cb3adf9da4b573ae0c499d49be91995aa3e38b5d9a41649adfaf986040c%402%3Frelay-protocol%3Diridium%26symKey%3D618e22482db56c3dda38b52f7bfca9515cc307f413694c1d6d91931bbe00ae90
// wc:83ba9cb3adf9da4b573ae0c499d49be91995aa3e38b5d9a41649adfaf986040c@2?relay-protocol=iridium&symKey=618e22482db56c3dda38b52f7bfca9515cc307f413694c1d6d91931bbe00ae90
private fun dispatchWalletConnect(uri: Uri): Boolean {
    return runCatching {
        val data = getWalletConnectUri(uri)

        if (data.isNullOrBlank() || !data.startsWith("wc:")) {
            loge(TAG, "Invalid WalletConnect URI format: $data")
            return@runCatching false
        }
        
        if (!WalletConnect.isInitialized()) {
            logd(TAG, "WalletConnect is not initialized, initializing...")
            var result = false
            ioScope {
                val initResult = withTimeoutOrNull(3000) {
                    var waitTime = 50L
                    var attempts = 0
                    val maxAttempts = 5

                    while (!WalletConnect.isInitialized() && attempts < maxAttempts) {
                        delay(waitTime)
                        attempts++
                        waitTime = minOf(waitTime * 2, 500)
                    }

                    WalletConnect.isInitialized()
                }
                if (initResult == null) {
                    loge(TAG, "WalletConnect initialization timeout")
                    uiScope {
                        toast(R.string.wallet_connect_error)
                    }
                    result = false
                } else {
                    try {
                        WalletConnect.get().pair(data.toString())
                        // Wait for session proposal to be handled
                        result = true
                    } catch (e: Exception) {
                        loge(TAG, "WalletConnect pairing failed: ${e.message}")
                        loge(e)
                        result = false
                    }
                }
            }
            result
        } else {
            try {
                WalletConnect.get().pair(data.toString())
                // Wait for session proposal to be handled
                true
            } catch (e: Exception) {
                loge(TAG, "WalletConnect pairing failed: ${e.message}")
                loge(e)
                false
            }
        }
    }.getOrDefault(false)
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
                val decoded = URLDecoder.decode(it, StandardCharsets.UTF_8.name())
                decoded
            } else {
                it
            }
        }
    }.onFailure { e ->
        loge(TAG, "Error processing WalletConnect URI: ${e.message}")
        loge(e)
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