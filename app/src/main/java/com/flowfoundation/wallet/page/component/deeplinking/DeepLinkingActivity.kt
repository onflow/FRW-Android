package com.flowfoundation.wallet.page.component.deeplinking

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.utils.logd

private val TAG = DeepLinkingActivity::class.java.simpleName

class DeepLinkingActivity : BaseActivity() {

    private val isPendingAction: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_PENDING_ACTION, false)
    }

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
        if (!isPendingAction) {
            MainActivity.launch(this)
        }
        dispatchDeepLinking(uri)
        finish()
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    companion object {
        private const val EXTRA_PENDING_ACTION = "extra_pending_action"
        fun openPendingAction(context: Context, uri: Uri) {
            logd(TAG, "openPendingAction:$uri")
            context.startActivity(Intent(context, DeepLinkingActivity::class.java).apply {
                data = uri
                putExtra(EXTRA_PENDING_ACTION, true)
            })
        }
    }
}