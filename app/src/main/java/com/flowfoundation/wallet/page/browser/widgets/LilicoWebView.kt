package com.flowfoundation.wallet.page.browser.widgets

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.text.Html
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import com.flowfoundation.wallet.page.browser.extractActualHost
import com.flowfoundation.wallet.page.browser.hasDeceptiveAtSymbol
import com.crowdin.platform.Crowdin
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.blocklist.BlockManager
import com.flowfoundation.wallet.manager.evm.loadInitJS
import com.flowfoundation.wallet.manager.evm.loadProviderJS
import com.flowfoundation.wallet.page.browser.subpage.filepicker.showWebviewFilePicker
import com.flowfoundation.wallet.page.component.deeplinking.DeepLinkScheme
import com.flowfoundation.wallet.page.component.deeplinking.UriHandler
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.ioScope
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

        setLayerType(View.LAYER_TYPE_HARDWARE, null)
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

                blockedViewLayout = LayoutInflater.from(context).inflate(R.layout.layout_blocked_view, parent, false)

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

    /**
     * Shows a security warning dialog for URLs that contain deceptive patterns
     * like "@" symbols that could be used for URL spoofing attacks.
     *
     * @param url The potentially deceptive URL
     * @param actualHost The actual host that will be accessed
     * @param onProceed Callback when user chooses to proceed anyway
     * @param onCancel Callback when user cancels navigation
     */
    private fun showDeceptiveUrlWarning(
        url: String,
        actualHost: String,
        onProceed: () -> Unit,
        onCancel: () -> Unit
    ) {
        uiScope {
            AlertDialog.Builder(context)
                .setTitle(R.string.security_warning)
                .setMessage(context.getString(R.string.deceptive_url_warning, actualHost))
                .setPositiveButton(R.string.proceed_anyway) { dialog, _ ->
                    dialog.dismiss()
                    onProceed()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                    onCancel()
                }
                .setCancelable(false)
                .show()
        }
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

        /**
         * SECURITY FIX: Override JavaScript alert dialogs to clearly indicate
         * they are from a webpage, not from Flow Wallet app itself.
         * This prevents phishing attacks via fake app dialogs.
         */
        override fun onJsAlert(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {
            val host = url?.extractActualHost() ?: "Unknown"
            logd(TAG, "SECURITY: JS Alert intercepted from: $host")

            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.js_dialog_title, host))
                .setMessage(message)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    result?.confirm()
                    dialog.dismiss()
                }
                .setOnCancelListener {
                    result?.cancel()
                }
                .setCancelable(true)
                .show()
            return true
        }

        /**
         * SECURITY FIX: Override JavaScript confirm dialogs to clearly indicate
         * they are from a webpage, not from Flow Wallet app itself.
         */
        override fun onJsConfirm(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {
            val host = url?.extractActualHost() ?: "Unknown"
            logd(TAG, "SECURITY: JS Confirm intercepted from: $host")

            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.js_dialog_title, host))
                .setMessage(message)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    result?.confirm()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    result?.cancel()
                    dialog.dismiss()
                }
                .setOnCancelListener {
                    result?.cancel()
                }
                .setCancelable(true)
                .show()
            return true
        }

        /**
         * SECURITY FIX: Override JavaScript prompt dialogs to clearly indicate
         * they are from a webpage and NOT from Flow Wallet app.
         * This is critical to prevent phishing attacks that display fake login prompts.
         *
         * The dialog clearly shows which website is requesting input, making it
         * obvious to users that this is NOT a Flow Wallet system dialog.
         */
        override fun onJsPrompt(
            view: WebView?,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult?
        ): Boolean {
            val host = url?.extractActualHost() ?: "Unknown"
            logd(TAG, "SECURITY: JS Prompt intercepted from: $host - Message: $message")

            // Create an EditText for user input
            val inputView = EditText(context).apply {
                setText(defaultValue)
                hint = context.getString(R.string.js_prompt_hint)
                setPadding(48, 32, 48, 32)
            }

            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.js_dialog_title, host))
                .setMessage(message)
                .setView(inputView)
                .setPositiveButton(R.string.ok) { dialog, _ ->
                    result?.confirm(inputView.text.toString())
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    result?.cancel()
                    dialog.dismiss()
                }
                .setOnCancelListener {
                    result?.cancel()
                }
                .setCancelable(true)
                .show()
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
                ioScope {
                    val initJS = loadInitJS()
                    uiScope {
                        view?.evaluateJavascript(initJS, null)
                    }
                }
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

            // Handle null request or URL
            if (request?.url == null) {
                logd(TAG, "shouldOverrideUrlLoading: Request or URL is null")
                return super.shouldOverrideUrlLoading(view, request)
            }

            val uri = request.url
            val urlString = uri.toString()
            logd(TAG, "shouldOverrideUrlLoading URL: $uri, scheme: ${uri.scheme}")

            // Check if it's an about:blank#blocked URL (internal for blocked pages)
            if (urlString == "about:blank#blocked") {
                return true
            }

            // SECURITY FIX: Block javascript: URL scheme to prevent UXSS attacks
            // Attackers can use javascript: URLs in iframes to execute arbitrary JS
            // and display fake UI elements (like login prompts) that look like app dialogs
            if (uri.scheme?.lowercase() == "javascript") {
                logd(TAG, "SECURITY: Blocked javascript: URL scheme - $urlString")
                return true
            }

            // SECURITY CHECK: Detect deceptive URLs with @ symbol
            // URLs like "https://trusted.com@malicious.com" are spoofing attempts
            if (urlString.hasDeceptiveAtSymbol()) {
                val actualHost = urlString.extractActualHost()
                logd(TAG, "SECURITY: Deceptive URL detected. URL: $urlString, Actual host: $actualHost")

                // Stop loading and show warning
                view?.stopLoading()
                isLoading = false

                showDeceptiveUrlWarning(
                    url = urlString,
                    actualHost = actualHost,
                    onProceed = {
                        // User chose to proceed despite warning
                        logd(TAG, "User proceeded to deceptive URL: $urlString")
                        isLoading = true
                        view?.loadUrl(urlString)
                    },
                    onCancel = {
                        // User cancelled navigation
                        logd(TAG, "User cancelled navigation to deceptive URL: $urlString")
                    }
                )
                return true
            }

            // Check if URL is blocked - this must be done on UI thread
            uiScope {
                if (BlockManager.isBlocked(urlString)) {
                    logd(TAG, "URL blocked: $uri")
                    showBlockedViewLayout(urlString)
                    loadUrl("about:blank#blocked")
                    isLoading = false
                }
            }

            // Process URI with UriHandler
            val handled = UriHandler.processUri(context, uri)
            if (handled) {
                // If URI was handled by UriHandler, stop WebView navigation
                view?.stopLoading()
                if (uri.scheme == DeepLinkScheme.TG.scheme) {
                    // For Telegram URLs, also clear history to avoid loops
                    view?.clearHistory()
                }
                return true
            }

            // If the URL wasn't handled by our custom handlers, let WebView handle it
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
