package com.flowfoundation.wallet.page.component.deeplinking

import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.utils.logd

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

        logd(TAG, "uri:$uri")

        MainActivity.launch(this)
        dispatchDeepLinking(uri)
        finish()
    }

    override fun onPause() {
        super.onPause()
        finish()
    }
}