package com.flowfoundation.wallet.page.component.deeplinking

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import kotlinx.coroutines.delay

private val TAG = DeepLinkingActivity::class.java.simpleName

class DeepLinkingActivity : BaseActivity() {

    private var isWalletConnectUri = false
    private var processingCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this))
        UltimateBarX.with(this).color(Color.TRANSPARENT).fitWindow(false).light(false).applyStatusBar()

        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }

        logd(TAG, "DeepLinkingActivity received uri: $uri")
        
        // Check if it's a WalletConnect URI
        isWalletConnectUri = isWalletConnectUri(uri)
        logd(TAG, "URI is WalletConnect: $isWalletConnectUri")
        
        // Launch MainActivity first
        MainActivity.launch(this)
        
        ioScope {
            try {
                // For WalletConnect URIs, we need a small delay to ensure MainActivity is ready
                if (isWalletConnectUri) {
                    logd(TAG, "Delaying WalletConnect processing to ensure MainActivity is ready")
                    delay(1000)
                }
                
                dispatchDeepLinking(this@DeepLinkingActivity, uri)
                logd(TAG, "DeepLinkingDispatch completed for uri: $uri")
                
                // For WalletConnect, wait a bit longer to ensure the connection dialog has time to show
                if (isWalletConnectUri) {
                    logd(TAG, "Adding additional delay for WalletConnect to ensure dialog shows")
                    delay(1500)
                }
            } catch (e: Exception) {
                logd(TAG, "Error in DeepLinkingDispatch: ${e.message}")
                loge(e)
            } finally {
                processingCompleted = true
                if (!isWalletConnectUri) {
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // If processing is completed and this is a WalletConnect URI,
        // finish the activity as it's likely we've returned from MainActivity
        if (processingCompleted && isWalletConnectUri) {
            finish()
        }
    }

    private fun isWalletConnectUri(uri: Uri): Boolean {
        return try {
            val uriString = uri.toString()
            val wcUriEncoded = getWalletConnectUri(uri)
            
            wcUriEncoded?.startsWith("wc:") == true
        } catch (e: Exception) {
            loge(TAG, "Error determining if URI is WalletConnect: ${e.message}")
            false
        }
    }
}