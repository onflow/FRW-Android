package com.flowfoundation.wallet.page.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.page.browser.widgets.LilicoWebView
import com.flowfoundation.wallet.utils.extensions.removeFromParent
import com.flowfoundation.wallet.utils.loge

class WebViewActivity : BaseActivity() {

    private val url by lazy { intent.getStringExtra(URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)
        findViewById<LilicoWebView>(R.id.webview).apply {
            loadUrl(this@WebViewActivity.url!!)
        }
    }

    override fun onDestroy() {
        // Without an explicit destroy the chromium-side resources backing the
        // WebView outlive this Activity and accumulate across launches. That
        // leak is one of the contributors to the OOM crashes reported against
        // browser-adjacent screens.
        runCatching {
            findViewById<LilicoWebView>(R.id.webview)?.let { web ->
                web.stopLoading()
                web.setWebViewCallback(null)
                web.loadUrl("about:blank")
                web.clearHistory()
                web.removeFromParent()
                web.removeAllViews()
                web.destroy()
            }
        }.onFailure { loge(it) }
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        private const val URL = "url"
        fun launch(context: Context, url: String?) {
            url ?: return
            context.startActivity(Intent(context, WebViewActivity::class.java).apply {
                putExtra(URL, url)
            })
        }
    }
}