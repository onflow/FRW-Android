package com.flowfoundation.wallet.page.browser.presenter

import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.zackratos.ultimatebarx.ultimatebarx.navigationBarHeight
import com.zackratos.ultimatebarx.ultimatebarx.statusBarHeight
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.LayoutBrowserBinding
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.browser.*
import com.flowfoundation.wallet.page.browser.model.BrowserModel
import com.flowfoundation.wallet.page.browser.tools.*
import com.flowfoundation.wallet.page.browser.widgets.BrowserPopupMenu
import com.flowfoundation.wallet.page.browser.widgets.WebviewCallback
import com.flowfoundation.wallet.page.evm.EnableEVMDialog
import com.flowfoundation.wallet.reactnative.ReactNativeActivity
import com.flowfoundation.wallet.reactnative.bridge.RNBridge
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.page.window.WindowFrame
import com.flowfoundation.wallet.page.window.bubble.tools.inBubbleStack
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.logd

class BrowserPresenter(
    private val binding: LayoutBrowserBinding,
    private val viewModel: BrowserViewModel,
) : BasePresenter<BrowserModel>, WebviewCallback {

    private fun webview() = browserTabLast()?.webView

    init {
        with(binding) {
            contentWrapper.post {
                statusBarHolder.layoutParams.height = statusBarHeight
                with(root) {
                    val navBarHeight = if (navigationBarHeight < 50) 0 else navigationBarHeight
                    setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom + navBarHeight)
                }
            }
            with(binding) {
                refreshButton.setOnClickListener { webview()?.reload() }
                backButton.setOnClickListener { handleBackPressed() }
                moveButton.setOnClickListener {
                    val activity =
                        BaseActivity.getCurrentActivity() ?: return@setOnClickListener
                    if (WalletManager.haveChildAccount() || WalletManager.isChildAccountSelected() || EVMWalletManager.haveEVMAddress()) {
                        // Launch React Native send workflow directly instead of MoveDialog
                        ReactNativeActivity.launch(activity, RNBridge.ScreenType.SEND_ASSET)
                        // Minimize browser to allow React Native to show prominently
                        shrinkBrowser()
                    } else {
                        uiScope {
                            EnableEVMDialog.show(activity.supportFragmentManager)
                        }
                    }
                }
                floatButton.setOnClickListener { shrinkBrowser() }
                menuButton.setOnClickListener { browserTabLast()?.let {
                    BrowserPopupMenu(menuButton, it).show()
                } }
            }
        }
    }

    override fun bind(model: BrowserModel) {
        model.url?.let { onOpenNewUrl(it) }
        model.onPageClose?.let { browserTabLast()?.webView?.saveRecentRecord() }
        model.removeTab?.let { removeTab(it) }
        model.onTabChange?.let { onBrowserTabChange() }
    }

    override fun onScrollChange(scrollY: Int, offset: Int) {

    }


    override fun onProgressChange(progress: Float) {
        binding.progressBar.setProgress(progress)
    }

    override fun onTitleChange(title: String) {
        // SECURITY FIX: Do NOT use the page title for address bar display
        // The title can be spoofed by attackers to show fake URLs
        // Instead, we always display the actual URL via onPageUrlChange
        // The title is intentionally ignored here to prevent address bar spoofing
        logd(TAG, "Page title received (not displayed in address bar): $title")
    }

    override fun onPageUrlChange(url: String, isReload: Boolean) {
        // SECURITY FIX: Always display the actual sanitized URL, never the page title
        updateAddressBar(url)
    }

    /**
     * Safely updates the address bar with the actual URL being displayed.
     * This method sanitizes URLs to prevent spoofing attacks.
     *
     * @param url The URL to display (will be sanitized)
     */
    private fun updateAddressBar(url: String) {
        if (url.isBlank()) return

        // Use the sanitized URL that strips out deceptive @ symbols
        val safeUrl = url.toSafeDisplayUrl()

        // Log if a deceptive URL was detected
        if (url.hasDeceptiveAtSymbol()) {
            logd(TAG, "SECURITY: Deceptive URL detected with @ symbol. Original: $url, Displayed: $safeUrl")
        }

        binding.titleView.text = safeUrl.extractActualHost()
    }

    companion object {
        private const val TAG = "BrowserPresenter"
    }

    override fun onWindowColorChange(color: Int) {
//        binding.statusBarHolder.setBackgroundColor(color)
    }

    private fun removeTab(tab: BrowserTab) {
        popBrowserTab(tab.id)
        showBrowserLastTab()
    }

    private fun onOpenNewUrl(url: String) {
        WindowFrame.browserContainer()?.setVisible(true)
        newAndPushBrowserTab(url)?.let { tab ->
            tab.webView.setWebViewCallback(this@BrowserPresenter)
            WindowFrame.browserContainer()?.post { expandBrowser() }
            // SECURITY FIX: Always display the URL, never the page title
            updateAddressBar(tab.url().orEmpty())
        }
    }

    private fun onBrowserTabChange() {
        WindowFrame.browserContainer()?.setVisible(true)
        // SECURITY FIX: Always display the URL, never the page title
        updateAddressBar(webview()?.url.orEmpty())
    }

    fun handleBackPressed(): Boolean {
        // bubble mode
        if (!binding.root.isVisible()) {
            return false
        }
        val lastTab = browserTabLast()
        when {
            isSearchBoxVisible() -> viewModel.hideInputPanel()
            webview()?.canGoBack() ?: false -> webview()?.goBack()
            lastTab != null && inBubbleStack(lastTab) -> shrinkBrowser()
            lastTab != null -> removeTabAndHideBrowser(lastTab.id)
            else -> return false
        }
        return true
    }

    private fun isSearchBoxVisible() = binding.inputLayout.root.isVisible()
}
