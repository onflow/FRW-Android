package com.flowfoundation.wallet.page.component.deeplinking

import android.net.Uri
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder

private const val TAG = "DeepLinkingDispatch"

fun dispatchDeepLinking(uri: Uri) {
    ioScope { dispatchWalletConnect(uri) }
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
        if (uriParamStart != -1) {
            uriString.substring(uriParamStart + 4)
        } else {
            uri.getQueryParameter("uri")
        }
    }.getOrNull()
}
