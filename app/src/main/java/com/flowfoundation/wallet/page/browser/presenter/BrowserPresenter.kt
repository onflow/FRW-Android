package com.flowfoundation.wallet.page.browser.presenter

import com.flowfoundation.wallet.base.activity.BaseActivity
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
import com.flowfoundation.wallet.page.wallet.dialog.MoveDialog
import com.flowfoundation.wallet.page.window.WindowFrame
import com.flowfoundation.wallet.page.window.bubble.tools.inBubbleStack
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.uiScope

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
                        uiScope {
                            MoveDialog().showMove(activity.supportFragmentManager)
                        }
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
        binding.titleView.text = title.ifBlank { webview()?.url }
    }

    override fun onPageUrlChange(url: String, isReload: Boolean) {
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
            expandBrowser()
            onTitleChange(tab.title() ?: (tab.url().orEmpty()))
        }
    }

    private fun onBrowserTabChange() {
        WindowFrame.browserContainer()?.setVisible(true)
        onTitleChange(webview()?.title.orEmpty())
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