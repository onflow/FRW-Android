package com.flowfoundation.wallet.page.window.bubble.presenter

import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.WindowBubbleBinding
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.page.window.bubble.model.BubbleModel
import com.flowfoundation.wallet.page.window.bubble.model.icon
import com.flowfoundation.wallet.page.window.bubble.onBubbleClick
import com.flowfoundation.wallet.page.window.bubble.tools.bubbleTabs
import com.flowfoundation.wallet.page.window.bubble.tools.clearBubbleTabs
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.DraggableLayout
import com.flowfoundation.wallet.widgets.floatwindow.widgets.WindowRemoveLayout

class BubblePresenter(
    private val binding: WindowBubbleBinding,
) : BasePresenter<BubbleModel> {

    private val removeLayout by lazy { WindowRemoveLayout(binding.root, binding.floatBubble) { clearBubbleTabs() } }

    init {
        with(binding) {
            floatBubble.setOnClickListener { onBubbleClick() }
            floatBubble.addOnDragListener(removeLayout)
            floatBubble.addOnDragListener(BubbleDragListener())
            floatBubble.setVisible(bubbleTabs().isNotEmpty(), invisible = true)
        }
    }

    override fun bind(model: BubbleModel) {
        model.onTabChange?.let { onTabChange() }
        model.onVisibleChange?.let { onVisibleChange(it) }
    }

    private fun onVisibleChange(isVisible: Boolean) {
        binding.floatBubble.setVisible(isVisible && bubbleTabs().isNotEmpty(), invisible = true)
    }

    private fun onTabChange() {
        with(binding) {
            floatBubble.setVisible(bubbleTabs().isNotEmpty() && !bubbleStackWrapper.isVisible(), invisible = true)
            val tab = bubbleTabs().lastOrNull() ?: return
            Glide.with(Env.getApp()).load(tab.icon()).into(iconView)

            progressBar.setVisible(tab.data is TransactionState)
            (tab.data as? TransactionState)?.let { progressBar.setProgress((it.progress() * 100).toInt(), true) }
        }
    }

    private inner class BubbleDragListener : DraggableLayout.OnDragListener {
        override fun onDrag(originX: Float, originY: Float, x: Float, y: Float) {}

        override fun onDragEnd() {
            with(binding) {
                floatBubble.setBackgroundResource(if (binding.floatBubble.isNearestLeft) R.drawable.bg_round_right_12dp else R.drawable.bg_round_left_12dp)
            }
        }

        override fun onDragStart() {
            binding.floatBubble.setBackgroundResource(R.drawable.bg_round_12dp)
        }
    }
}