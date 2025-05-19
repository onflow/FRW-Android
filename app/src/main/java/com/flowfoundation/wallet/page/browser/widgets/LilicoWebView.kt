package com.flowfoundation.wallet.page.browser.widgets

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.text.Html
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import com.crowdin.platform.Crowdin
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.blocklist.BlockManager
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

    private lateinit var blockedViewLayout: View
    private lateinit var tvBlockedUrl: TextView
    private lateinit var tvBlockedInfo: TextView
    private lateinit var tvIgnoreWarning: TextView

    private var blockedUrl: String? = null

    constructor(context: Context) : super(Crowdin.wrapContext(context)) {
        initWebView()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(Crowdin.wrapContext(context), attrs) {
        initWebView()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        Crowdin.wrapContext(context),
        attrs,
        defStyleAttr
    ) {
        initWebView()
    }

    private fun initWebView() {
        // Set default layout parameters
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Disable hardware acceleration for this WebView to prevent some layout issues
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        initBlockedViewLayout()

        with(settings) {
            loadsImagesAutomatically = true
            javaScriptEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
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

    private fun initBlockedViewLayout() {
        post {
            if (parent is ViewGroup) {
                val parent = parent as ViewGroup

                for (i in 0 until parent.childCount) {
                    val child = parent.getChildAt(i)
                    if (child.id == R.id.cl_blocked_view) {
                        blockedViewLayout = child
                        setupBlockedViewLayout()
                        return@post
                    }
                }

                blockedViewLayout = LayoutInflater.from(context)
                    .inflate(R.layout.layout_blocked_view, parent, false)

                val layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                blockedViewLayout.layoutParams = layoutParams

                blockedViewLayout.visibility = View.GONE

                parent.addView(blockedViewLayout)

                setupBlockedViewLayout()
            }
        }
    }

    private fun setupBlockedViewLayout() {
        tvBlockedUrl = blockedViewLayout.findViewById(R.id.tv_blocked_url)
        tvBlockedInfo = blockedViewLayout.findViewById(R.id.tv_blocked_info)
        tvIgnoreWarning = blockedViewLayout.findViewById(R.id.tv_ignore_warning)

        tvBlockedInfo.text =
            Html.fromHtml(context.getString(R.string.blocked_info), Html.FROM_HTML_MODE_LEGACY)
        tvBlockedInfo.setOnClickListener {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Outblock/flow-blocklist"))
            context.startActivity(intent)
        }

        val content = SpannableString(context.getString(R.string.ignore_warning))
        content.setSpan(UnderlineSpan(), 0, content.length, 0)
        tvIgnoreWarning.text = content

        tvIgnoreWarning.setOnClickListener {
            hideBlockedViewLayout()
            blockedUrl?.let { url ->
                blockedUrl = null
                loadUrl(url)
            }
        }
    }

    private fun showBlockedViewLayout(url: String) {
        blockedUrl = url

        val uri = Uri.parse(url)
        val host = uri.host ?: url

        val blockedUrlText = context.getString(R.string.blocked_url, host)
        tvBlockedUrl.text = blockedUrlText

        blockedViewLayout.visibility = View.VISIBLE
    }

    private fun hideBlockedViewLayout() {
        blockedViewLayout.visibility = View.GONE
    }

    fun setWebViewCallback(callback: WebviewCallback?) {
        this.callback = callback
    }

    fun getCallback(): WebviewCallback? {
        return callback
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
            try {
                isLoading = true
                logd(TAG, "onPageStarted")

                view.executeJs(JS_FCL_EXTENSIONS)
                view.executeJs(JS_LISTEN_WINDOW_FCL_MESSAGE)
                view.executeJs(JS_LISTEN_FLOW_WALLET_TRANSACTION)
                view?.evaluateJavascript(loadProviderJS(), null)
                view?.evaluateJavascript(loadInitJS(), null)
            } catch (e: Exception) {
                logd(TAG, "Error in onPageStarted: ${e.message}")
                isLoading = false
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            try {
                isLoading = false
                logd(TAG, "onPageFinished")
                view ?: return
                val padding = 20f.dp2px()
                val jsCode = "document.body.style.paddingBottom = '${padding}px';"
                view.evaluateJavascript(jsCode, null)
            } catch (e: Exception) {
                logd(TAG, "Error in onPageFinished: ${e.message}")
                isLoading = false
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            logd(TAG, "WebView error: ${error?.description}")
            isLoading = false
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
                logd(TAG, "shouldOverrideUrlLoading URL: $it, scheme: ${it.scheme}")

                if (it.scheme == "wc") {
                    logd(TAG, "Handling WalletConnect URI")
                    WalletConnect.get().pair(it.toString())
                    return true
                } else if ((it.scheme == "frw" || it.scheme == "fcw") && it.path?.startsWith("/wc") == true) {
                    logd(TAG, "Handling ${it.scheme}://wc URI")
                    val uri = getWalletConnectUri(it)
                    uri?.let { wcUri ->
                        WalletConnect.get().pair(wcUri)
                    }
                    return true
                } else if (it.scheme == "intent") {
                    return try {
                        logd(TAG, "Handling Intent URI")
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        }
                        true
                    } catch (e: URISyntaxException) {
                        e.printStackTrace()
                        false
                    }
                } else if (it.scheme == "tg") {
                    val openTg = Intent(Intent.ACTION_VIEW, it)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    try {
                        context.startActivity(openTg)
                    } catch (e: Exception) {
                        Toast.makeText(context, R.string.telegram_not_installed, Toast.LENGTH_SHORT).show()
                    }

                    // Stop the WebView navigation to avoid looping
                    view?.stopLoading()
                    view?.clearHistory()
                    return true
                } else if (it.host == "link.lilico.app" || it.host == "frw-link.lilico.app" || it
                        .host == "fcw-link.lilico.app" || it.host == "link.wallet.flow.com"
                ) {
                    logd(TAG, "Handling wallet link URI")
                    safeRun {
                        val wcUri = getWalletConnectUri(it)
                        wcUri?.let { uri ->
                            logd(TAG, "Wallet Connect URI: $uri")
                            WalletConnect.get().pair(uri)
                        }
                    }
                    return true
                } else if (it.toString() == "about:blank#blocked") {
                    return true
                }
                uiScope {
                    if (BlockManager.isBlocked(it.toString())) {
                        logd(TAG, "URL blocked: $url")
                        showBlockedViewLayout(it.toString())
                        loadUrl("about:blank#blocked")
                        isLoading = false
                        return@uiScope
                    }
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