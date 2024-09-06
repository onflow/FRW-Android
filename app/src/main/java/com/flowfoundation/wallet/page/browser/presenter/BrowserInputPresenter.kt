package com.flowfoundation.wallet.page.browser.presenter

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.Fade
import androidx.transition.Scene
import androidx.transition.TransitionManager
import com.zackratos.ultimatebarx.ultimatebarx.navigationBarHeight
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.LayoutBrowserBinding
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.page.browser.BrowserViewModel
import com.flowfoundation.wallet.page.browser.adapter.BrowserRecommendWordsAdapter
import com.flowfoundation.wallet.page.browser.browserViewModel
import com.flowfoundation.wallet.page.browser.model.BrowserInputModel
import com.flowfoundation.wallet.page.browser.releaseBrowser
import com.flowfoundation.wallet.page.browser.toSearchUrl
import com.flowfoundation.wallet.page.browser.tools.browserTabLast
import com.flowfoundation.wallet.page.browser.tools.browserTabsCount
import com.flowfoundation.wallet.page.browser.tools.startSearchBoxAnimation
import com.flowfoundation.wallet.utils.extensions.*
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class BrowserInputPresenter(
    private val binding: LayoutBrowserBinding,
    private val viewModel: BrowserViewModel,
) : BasePresenter<BrowserInputModel> {

    private val inputBinding = binding.inputLayout

    private val recommendAdapter by lazy { BrowserRecommendWordsAdapter() }

    private val keyboardObserver by lazy { keyboardObserver() }

    init {
        binding.textWrapper.setOnClickListener { openFromWebViewTitle() }
        with(inputBinding.recyclerView) {
            adapter = recommendAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 5.dp2px().toInt(), ColorDividerItemDecoration.VERTICAL))
        }

        with(inputBinding.searchBox.inputView) {
            doOnTextChanged { _, _, _, _ -> onKeywordChange() }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val keyword = text.toString()
                    if (AppConfig.useInAppBrowser().not()) {
                        keyword.toSearchUrl().openInSystemBrowser(context, true)
                    } else {
                        viewModel.updateUrl(keyword.toSearchUrl())
                    }
                    clearPage()
                }
                return@setOnEditorActionListener false
            }
        }

        with(inputBinding) {
            searchBox.clearButton.setOnClickListener { searchBox.inputView.setText("") }
            searchBox.cancelButton.setOnClickListener { clearPage(isCancel = true) }
        }
    }

    private fun onKeywordChange() {
        with(inputBinding) {
            val keyword = searchBox.inputView.text.toString()
            viewModel.queryRecommendWord(keyword)

            val cancelVisible = keyword.isNotEmpty()
            if (searchBox.cancelWrapper.isVisible() != cancelVisible) {
                TransitionManager.go(Scene(inputBinding.root), Fade().apply { duration = 150 })
                searchBox.cancelWrapper.setVisible(cancelVisible)
            }
        }
    }

    override fun bind(model: BrowserInputModel) {
        model.onLoadNewUrl?.let { clearPage() }
        model.onHideInputPanel?.let { clearPage() }
        model.recommendWords?.let { recommendAdapter.setNewDiffData(it) }
        model.onPageAttach?.let { observeKeyboardVisible() }
        model.onPageDetach?.let { removeObserveKeyboardVisible() }
        model.searchBoxPosition?.let { openFromSearchBox(it) }
    }

    private fun observeKeyboardVisible() {
        binding.root.post { binding.root.viewTreeObserver.addOnGlobalLayoutListener(keyboardObserver) }
    }


    private fun removeObserveKeyboardVisible() {
        with(binding.root.viewTreeObserver) {
            if (isAlive) {
                removeOnGlobalLayoutListener(keyboardObserver)
            }
        }
    }

    private fun keyboardObserver(): ViewTreeObserver.OnGlobalLayoutListener {
        return ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            binding.root.getWindowVisibleDisplayFrame(rect)
            val contentHeight = binding.root.rootView.height

            val isKeyboardVisible = contentHeight - rect.bottom > contentHeight * 0.15f
            with(inputBinding.root) {
                inputBinding.root.setPadding(
                    paddingLeft,
                    paddingTop,
                    paddingRight,
                    if (isKeyboardVisible) contentHeight - rect.bottom - navigationBarHeight else 0,
                )
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearPage(isCancel: Boolean = false) {
        with(inputBinding.searchBox.inputView) {
            hideKeyboard()
            setText("")
        }
        recommendAdapter.setNewDiffData(emptyList())
        recommendAdapter.notifyDataSetChanged()
        browserViewModel()?.onSearchBoxHide()

        if (binding.webviewContainer.childCount == 0) {
            binding.root.setVisible(false)
        }

        inputBinding.root.setVisible(false)

        if (browserTabsCount() == 0 && isCancel) {
            releaseBrowser()
        }
    }

    private fun openFromSearchBox(point: Point) {
        startSearchBoxAnimation(binding.inputLayout, point)
        binding.root.setVisible()
        binding.inputLayout.root.setVisible()
        binding.inputLayout.searchBox.inputView.requestFocus()
        binding.inputLayout.searchBox.inputView.showKeyboard()
    }

    private fun openFromWebViewTitle() {
        binding.inputLayout.root.setVisible()
        with(inputBinding.searchBox) {
            with(inputView) {
                requestFocus()
                showKeyboard()
                setText(webview()?.url)
                setSelection(text.length)
                selectAll()
            }
            exploreSearchBox.root.setVisible(false)
            cancelWrapper.setVisible(true)
        }
    }

    private fun webview() = browserTabLast()?.webView

}