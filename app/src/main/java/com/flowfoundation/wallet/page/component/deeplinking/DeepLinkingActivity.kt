package com.flowfoundation.wallet.page.component.deeplinking

import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import kotlinx.coroutines.launch

private val TAG = DeepLinkingActivity::class.java.simpleName

class DeepLinkingActivity : BaseActivity() {

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
        MainActivity.launch(this)
        
        ioScope {
            try {
                dispatchDeepLinking(this@DeepLinkingActivity, uri)
                logd(TAG, "DeepLinkingDispatch completed for uri: $uri")
            } catch (e: Exception) {
                logd(TAG, "Error in DeepLinkingDispatch: ${e.message}")
                loge(e)
            } finally {
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Don't finish here, let the coroutine handle it
    }
}