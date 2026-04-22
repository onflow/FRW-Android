package com.flowfoundation.wallet.page.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.AndroidRuntimeException
import android.view.MenuItem
import android.widget.Toast
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.page.browser.widgets.LilicoWebView
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toast

class WebViewActivity : BaseActivity() {

    private val url by lazy { intent.getStringExtra(URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflating the WebView can throw AndroidRuntimeException wrapping
        // WebViewFactory$MissingWebViewPackageException on devices where the
        // Android System WebView package is missing, disabled, or being updated.
        // Crashing the activity in that case (issue #1256) leaves the user
        // with no recourse. Catch it, surface a clear message and finish.
        try {
            setContentView(R.layout.activity_webview)
            findViewById<LilicoWebView>(R.id.webview).apply {
                loadUrl(this@WebViewActivity.url!!)
            }
        } catch (e: AndroidRuntimeException) {
            loge(e)
            toast(R.string.webview_unavailable, Toast.LENGTH_LONG)
            finish()
        } catch (e: Exception) {
            loge(e)
            toast(R.string.webview_unavailable, Toast.LENGTH_LONG)
            finish()
        }
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