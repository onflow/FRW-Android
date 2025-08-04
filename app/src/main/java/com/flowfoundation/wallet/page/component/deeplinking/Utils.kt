package com.flowfoundation.wallet.page.component.deeplinking

import android.content.Context
import android.net.Uri
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.firebase.auth.isUserSignIn
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.networkId
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.ReactNativeDemoActivity
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.wallet.dialog.SwapDialog
import com.flowfoundation.wallet.utils.isRegistered
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import java.math.BigDecimal

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
    
    // Try to handle with the new UriHandler first
    if (uri.scheme == DeepLinkScheme.TG.scheme) {
        UriHandler.processUri(context, uri)
        return
    }
    
    // For backward compatibility, continue with the existing logic
    val wcUri = UriHandler.extractWalletConnectUri(uri)
    if (wcUri?.startsWith(DeepLinkScheme.WC.scheme + ":") == true) {
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
private suspend fun dispatchWalletConnect(uri: Uri): Boolean {
    return runCatching {
        val data = UriHandler.extractWalletConnectUri(uri)

        if (data.isNullOrBlank() || !data.startsWith(DeepLinkScheme.WC.scheme + ":")) {
            loge(TAG, "Invalid WalletConnect URI format: $data")
            uiScope {
                toast(R.string.wallet_connect_pairing_error)
            }
            return@runCatching false
        }
        
        // Initialize WalletConnect if needed
        if (!WalletConnect.isInitialized()) {
            logd(TAG, "WalletConnect is not initialized, waiting for initialization...")
            
            // Wait for WalletConnect to initialize with timeout
            val initialized = withTimeoutOrNull(10000) {
                var waitTime = 200L
                var attempts = 0
                val maxAttempts = 10
                
                while (!WalletConnect.isInitialized() && attempts < maxAttempts) {
                    logd(TAG, "Waiting for WalletConnect initialization, attempt ${attempts + 1} of $maxAttempts")
                    delay(waitTime)
                    attempts++
                    waitTime = minOf(waitTime * 2, 1000)
                }
                
                WalletConnect.isInitialized()
            } ?: false
            
            if (!initialized) {
                loge(TAG, "WalletConnect initialization failed or timed out")
                uiScope {
                    toast(R.string.wallet_connect_initialization_error)
                }
                return@runCatching false
            }
            
            logd(TAG, "WalletConnect successfully initialized")
        }
        
        // Get instance and proceed with pairing
        try {
            // Try to get an instance of WalletConnect and pair
            val wcInstance = WalletConnect.get()
            
            // Add a short delay to ensure all UI transitions are complete
            delay(300)
            
            // Call the improved pairing method
            logd(TAG, "Initiating WalletConnect pairing with URI: $data")
            wcInstance.pair(data)
            
            // Return success immediately, but the actual connection will happen asynchronously
            logd(TAG, "WalletConnect pairing initiated successfully")
            return@runCatching true
        
        } catch (e: Exception) {
            loge(TAG, "Error during WalletConnect pairing: ${e.message}")
            loge(e)
            uiScope {
                toast(R.string.wallet_connect_pairing_error)
            }
            return@runCatching false
        }
    }.getOrElse { e ->
        loge(TAG, "Unexpected error in WalletConnect dispatch: ${e.message}")
        loge(e)
        uiScope {
            toast(R.string.wallet_connect_generic_error)
        }
        false
    }
}

// No longer needed, use UriHandler.extractWalletConnectUri instead
@Deprecated("Use UriHandler.extractWalletConnectUri instead")
fun getWalletConnectUri(uri: Uri): String? {
    return UriHandler.extractWalletConnectUri(uri)
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
            // Launch React Native send workflow instead of native SendAmountActivity
            ReactNativeDemoActivity.launch(
                it,
                "SelectTokens",
                WalletManager.selectedWalletAddress().toAddress(),
                if (isTestnet()) "testnet" else "mainnet"
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