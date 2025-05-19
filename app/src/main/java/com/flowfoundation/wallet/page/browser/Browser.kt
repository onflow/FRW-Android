package com.flowfoundation.wallet.page.browser

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.databinding.LayoutBrowserBinding
import com.flowfoundation.wallet.page.browser.model.BrowserInputModel
import com.flowfoundation.wallet.page.browser.model.BrowserModel
import com.flowfoundation.wallet.page.browser.presenter.BrowserInputPresenter
import com.flowfoundation.wallet.page.browser.presenter.BrowserPresenter

class Browser : FrameLayout {
    private var binding: LayoutBrowserBinding = LayoutBrowserBinding.inflate(LayoutInflater.from(context))
    private var presenter: BrowserPresenter
    private var inputPresenter: BrowserInputPresenter

    private val viewModel by lazy { BrowserViewModel() }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        addView(binding.root)

        presenter = BrowserPresenter(binding, viewModel)
        inputPresenter = BrowserInputPresenter(binding, viewModel)

        with(viewModel) {
            onUrlUpdateLiveData = {
                presenter.bind(BrowserModel(url = it))
                inputPresenter.bind(BrowserInputModel(onLoadNewUrl = it))
            }
            recommendWordsLiveData = { inputPresenter.bind(BrowserInputModel(recommendWords = it)) }
            onHideInputPanel = { inputPresenter.bind(BrowserInputModel(onHideInputPanel = true)) }
            onRemoveBrowserTab = {
                presenter.bind(BrowserModel(removeTab = it))
            }
            onTabChange = { presenter.bind(BrowserModel(onTabChange = true)) }
            onSearchBoxHide = { focus() }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        focus()
        inputPresenter.bind(BrowserInputModel(onPageAttach = true))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        inputPresenter.bind(BrowserInputModel(onPageDetach = true))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (presenter.handleBackPressed()) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    fun loadUrl(url: String) {
        presenter.bind(BrowserModel(url = url))
    }

    fun open(params: BrowserParams) {
        focus()
        presenter.bind(BrowserModel(params = params))
        params.searchBoxPosition?.let {
            inputPresenter.bind(BrowserInputModel(searchBoxPosition = it))
        }
    }

    fun onRelease() {
        presenter.bind(BrowserModel(onPageClose = true))
    }

    fun viewModel() = viewModel

    fun binding() = binding

    private fun focus() {
        isFocusableInTouchMode = true
        isFocusable = true
        requestFocus()
    }
}