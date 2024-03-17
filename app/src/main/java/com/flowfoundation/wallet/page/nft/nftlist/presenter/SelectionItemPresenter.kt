package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.helper.widget.Carousel
import androidx.core.view.children
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.card.MaterialCardView
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.cache.NftSelections
import com.flowfoundation.wallet.databinding.ItemNftTopSelectionHeaderBinding
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.nftdetail.NftDetailActivity
import com.flowfoundation.wallet.page.nft.nftlist.NftViewModel
import com.flowfoundation.wallet.page.nft.nftlist.findParentAppBarLayout
import com.flowfoundation.wallet.page.nft.nftlist.widget.NftCardView
import com.flowfoundation.wallet.utils.ScreenUtils
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope

class SelectionItemPresenter(
    private val view: ViewGroup,
) : BaseViewHolder(view), BasePresenter<NftSelections> {
    private val binding by lazy { ItemNftTopSelectionHeaderBinding.bind(view.getChildAt(0)) }

    private val activity by lazy { findActivity(view) as FragmentActivity }

    private val viewModel by lazy { ViewModelProvider(activity)[NftViewModel::class.java] }

    private var currentIndex = 0

    private var data: List<Nft>? = null

    init {
        with(binding.motionLayout) {
            layoutParams.height = (ScreenUtils.getScreenWidth() * 0.7f + 32.dp2px()).toInt()
            setOnClickListener {
                data?.getOrNull(currentIndex)?.let {
                    NftDetailActivity.launch(activity, it.uniqueId())
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                children.forEach { child ->
                    if (child is MaterialCardView) {
                        child.outlineAmbientShadowColor = Color.TRANSPARENT
                        child.outlineSpotShadowColor = Color.TRANSPARENT
                        child.setCardBackgroundColor(ColorStateList.valueOf(Color.TRANSPARENT))
                    }
                }
            }
        }
    }

    override fun bind(model: NftSelections) {
        currentIndex = 0
        updateData(model.data)
    }

    private fun updateData(list: List<Nft>) {
        uiScope {
            this.data = list
            view.setVisible(list.isNotEmpty())
            binding.titleWrapper.setVisible(list.isNotEmpty())
            binding.motionLayout.setVisible(list.isNotEmpty())
            binding.carousel.setAdapter(SelectionsAdapter(list))
            binding.motionLayout.post { binding.carousel.jumpToIndex(currentIndex) }
            binding.carousel.refresh()
            viewModel.updateSelectionIndex(if (list.isEmpty()) -1 else currentIndex)
            findParentAppBarLayout(view)?.requestLayout()
        }
    }

    private inner class SelectionsAdapter(
        private val data: List<Nft>,
    ) : Carousel.Adapter {

        override fun count(): Int = data.size

        override fun populate(view: View?, index: Int) {
            val itemView = view as NftCardView
            itemView.bindData(data[index])
        }

        override fun onNewItem(index: Int) {
            logd(TAG, "onNewItem:$index")
            currentIndex = index
            viewModel.updateSelectionIndex(index)
        }
    }

    companion object {
        private val TAG = SelectionItemPresenter::class.java.simpleName
    }
}
