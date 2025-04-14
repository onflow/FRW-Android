package com.flowfoundation.wallet.page.window.bubble

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.databinding.WindowBubbleBinding
import com.flowfoundation.wallet.page.window.bubble.model.BubbleModel
import com.flowfoundation.wallet.page.window.bubble.model.FloatTabsModel
import com.flowfoundation.wallet.page.window.bubble.presenter.BubblePresenter
import com.flowfoundation.wallet.page.window.bubble.presenter.FloatTabsPresenter
import com.flowfoundation.wallet.utils.extensions.fadeTransition

class Bubble : FrameLayout {
    private var binding: WindowBubbleBinding = WindowBubbleBinding.inflate(LayoutInflater.from(context))
    private var floatTabsPresenter: FloatTabsPresenter
    private var bubblePresenter: BubblePresenter

    private val viewModel by lazy { BubbleViewModel() }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    init {
        addView(binding.root)

        floatTabsPresenter = FloatTabsPresenter(binding, viewModel)
        bubblePresenter = BubblePresenter(binding)

        with(viewModel) {
            onTabChange = {
                floatTabsPresenter.bind(FloatTabsModel(onTabChange = true))
                bubblePresenter.bind(BubbleModel(onTabChange = true))
            }

            onShowFloatTabs = {
                binding.root.fadeTransition(duration = 150)
                floatTabsPresenter.bind(FloatTabsModel(showTabs = true))
                bubblePresenter.bind(BubbleModel(onVisibleChange = false))
            }

            onHideFloatTabs = {
                binding.root.fadeTransition(duration = 150)
                floatTabsPresenter.bind(FloatTabsModel(closeTabs = true))
                bubblePresenter.bind(BubbleModel(onVisibleChange = true))
            }
        }
    }

    fun viewModel() = viewModel

    fun binding() = binding
}