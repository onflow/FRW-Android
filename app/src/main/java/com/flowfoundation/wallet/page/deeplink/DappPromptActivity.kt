package com.flowfoundation.wallet.page.deeplink

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.logd
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX

class DappPromptActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        UltimateBarX.with(this)
            .fitWindow(false)
            .light(!isNightMode(this))
            .applyStatusBar()
        UltimateBarX.with(this)
            .fitWindow(false)
            .light(!isNightMode(this))
            .applyNavigationBar()

        // 设置窗口背景透明，以便 Dialog 显示时透出底下的内容
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            DappPromptDialog(
                onConfirm = {
                    logd("DappPromptActivity", "User confirmed navigation to: $url")
                    openBrowser(this, url)
                    finish()
                },
                onDismiss = {
                    logd("DappPromptActivity", "User cancelled navigation")
                    finish()
                }
            )
        }
    }

    companion object {
        private const val EXTRA_URL = "extra_url"

        fun launch(context: Context, url: String) {
            val intent = Intent(context, DappPromptActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
            }
            context.startActivity(intent)
        }
    }
}
