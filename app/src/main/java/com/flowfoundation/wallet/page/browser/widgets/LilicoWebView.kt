package com.flowfoundation.wallet.page.browser.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.AttributeSet
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.annotation.ColorInt
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.manager.evm.loadInitJS
import com.flowfoundation.wallet.manager.evm.loadProviderJS
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.page.browser.subpage.filepicker.showWebviewFilePicker
import com.flowfoundation.wallet.page.component.deeplinking.getWalletConnectUri
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.safeRun
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.JS_FCL_EXTENSIONS
import com.flowfoundation.wallet.widgets.webview.JS_LISTEN_FLOW_WALLET_TRANSACTION
import com.flowfoundation.wallet.widgets.webview.JS_LISTEN_WINDOW_FCL_MESSAGE
import com.flowfoundation.wallet.widgets.webview.JS_QUERY_WINDOW_COLOR
import com.flowfoundation.wallet.widgets.webview.JsInterface
import com.flowfoundation.wallet.widgets.webview.evm.EvmInterface
import com.flowfoundation.wallet.widgets.webview.executeJs
import java.net.URISyntaxException

@SuppressLint("SetJavaScriptEnabled")
class LilicoWebView : WebView {
    private var callback: WebviewCallback? = null
    var isLoading = false

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
      : super(context, attrs, defStyleAttr)

    init {
        with(settings) {
            loadsImagesAutomatically = true
            javaScriptEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
        }
        setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        addJavascriptInterface(JsInterface(this), "android")
        addJavascriptInterface(EvmInterface(this), "_tw_")
        setOnScrollChangeListener { _, scrollX, scrollY, _, oldScrollY ->
            callback?.onScrollChange(scrollX, scrollY - oldScrollY)
        }

        safeRun {
            with(CookieManager.getInstance()) {
                setAcceptThirdPartyCookies(this@LilicoWebView, true)
                acceptCookie()
                setAcceptCookie(true)
            }
        }
    }

    fun setWebViewCallback(callback: WebviewCallback?) {
        this.callback = callback
    }

    private inner class WebChromeClient : android.webkit.WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            if (view.progress == newProgress) {
                callback?.onProgressChange(view.progress / 100f)
            }

            if (newProgress == 100) {
                logd(TAG, "load finish")
                view.executeJs(JS_QUERY_WINDOW_COLOR)
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            callback?.onTitleChange(title.orEmpty())
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            uiScope { showWebviewFilePicker(context, filePathCallback, fileChooserParams) }
            return true
        }
    }

    private inner class WebViewClient : android.webkit.WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            isLoading = true
            logd(TAG, "onPageStarted")
            view.executeJs(JS_FCL_EXTENSIONS)
            view.executeJs(JS_LISTEN_WINDOW_FCL_MESSAGE)
            view.executeJs(JS_LISTEN_FLOW_WALLET_TRANSACTION)
            view?.evaluateJavascript(loadProviderJS(), null)
            view?.evaluateJavascript(loadInitJS(), null)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            isLoading = false
            logd(TAG, "onPageFinished")
            view ?: return
            val padding = 20f.dp2px()
            val jsCode = "document.body.style.paddingBottom = '${padding}px';"
            view.evaluateJavascript(jsCode, null)
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            callback?.onPageUrlChange(url.orEmpty(), isReload)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            isLoading = true
            request?.url?.let {
                if (it.scheme == "wc") {
                    WalletConnect.get().pair(it.toString())
                    return true
                } else if (it.scheme == "intent") {
                    return try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        }
                        true
                    } catch (e: URISyntaxException) {
                        e.printStackTrace()
                        false
                    }
                } else if (it.host == "link.lilico.app" || it.host == "frw-link.lilico.app" || it.host == "fcw-link.lilico.app") {
                    safeRun {
                        WalletConnect.get().pair(getWalletConnectUri(it).toString())
                    }
                    return true
                } else if (it.toString() == "about:blank#blocked") {
                    return true
                }
            }
            return super.shouldOverrideUrlLoading(view, request)
        }
    }

    companion object {
        private val TAG = LilicoWebView::class.java.simpleName
    }
}

interface WebviewCallback {
    fun onScrollChange(scrollY: Int, offset: Int)
    fun onProgressChange(progress: Float)
    fun onTitleChange(title: String)
    fun onPageUrlChange(url: String, isReload: Boolean)
    fun onWindowColorChange(@ColorInt color: Int)
}