package com.flowfoundation.wallet.page.nft.nftlist.presenter

import NFTViewAdapter
import android.view.View
import android.widget.ListPopupWindow
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.statusBarHeight
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentNftBinding
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.nft.collectionlist.NftCollectionListActivity
import com.flowfoundation.wallet.page.nft.nftlist.NFTFragment
import com.flowfoundation.wallet.page.nft.nftlist.NftViewModel
import com.flowfoundation.wallet.page.nft.nftlist.adapter.NftListPageAdapter
import com.flowfoundation.wallet.page.nft.nftlist.model.NFTFragmentModel
import com.flowfoundation.wallet.utils.ScreenUtils
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.startShimmer
import java.lang.Float.min
import android.view.Gravity


class NFTFragmentPresenter(
    private val fragment: NFTFragment,
    private val binding: FragmentNftBinding,
) : BasePresenter<NFTFragmentModel> {

    private val viewModel by lazy { ViewModelProvider(fragment.requireActivity())[NftViewModel::class.java] }

    private var isTopSelectionExist = false

    init {
        with(binding) {
            with(toolbar) { post { setPadding(paddingLeft, paddingTop + statusBarHeight, paddingRight, paddingBottom) } }
            viewPager.adapter = NftListPageAdapter(fragment)
            addButton.setOnClickListener { NftCollectionListActivity.launch(fragment.requireContext()) }

            with(refreshLayout) {
                isEnabled = true
                setOnRefreshListener { viewModel.refresh() }
                setColorSchemeColors(R.color.colorSecondary.res2color())
            }

            binding.viewToggleButton.setOnClickListener { view ->
                showCustomPopupMenu(view)
            }
        }

        startShimmer(binding.shimmerLayout.shimmerLayout)
    }

    override fun bind(model: NFTFragmentModel) {
        binding.addButton.setVisible(WalletManager.isEVMAccountSelected().not() && WalletManager.isChildAccountSelected().not())
        model.favorite?.let {
            isTopSelectionExist = it.isNotEmpty()
            updateToolbarBackground()
        }
        model.onListScrollChange?.let { updateToolbarBackground(it) }
        model.listPageData?.let { binding.refreshLayout.isRefreshing = false }
    }

    private fun showCustomPopupMenu(anchorView: View) {
        val context = anchorView.context
        val initialIndex = if (viewModel.isGridViewLiveData.value == true) 1 else 0
        val adapter = NFTViewAdapter(context, initialIndex)

        val verticalOffset = context.resources.getDimensionPixelSize(R.dimen.popup_vertical_margin)

        val listPopupWindow = ListPopupWindow(context).apply {
            this.anchorView = anchorView
            setAdapter(adapter)
            isModal = true
            width = (ScreenUtils.getScreenWidth() * 0.3).toInt()
            setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.rounded_popup))
            setVerticalOffset(verticalOffset)

            setOnItemClickListener { _, _, position, _ ->
                if (position == 0) {
                    // Ignore header click
                    return@setOnItemClickListener
                }
                // Adjust index because header occupies position 0
                adapter.setSelectedIndex(position - 1)
                when (position - 1) {
                    0 -> {
                        viewModel.toggleViewType(false)
                        viewModel.toggleCollectionExpand()
                    }
                    1 -> {
                        viewModel.toggleViewType(true)
                        viewModel.toggleCollectionExpand()
                    }
                }
                dismiss()
            }
        }
        listPopupWindow.setDropDownGravity(Gravity.END)
        listPopupWindow.horizontalOffset = -context.resources.getDimensionPixelSize(R.dimen.popup_horizontal_margin)
        listPopupWindow.show()
    }


    private fun listPageScrollProgress(scrollY: Int): Float {
        val scroll = if (scrollY < 0) viewModel.listScrollChangeLiveData.value ?: 0 else scrollY
        val maxScrollY = ScreenUtils.getScreenHeight() * 0.25f
        return min(scroll / maxScrollY, 1f)
    }

    private fun updateToolbarBackground(scrollY: Int = -1) {
        if (isGridTabSelected()) {
            binding.toolbar.background.alpha = 255
        } else {
            if (!isTopSelectionExist) {
                // no selection
                binding.toolbar.background.alpha = 255
            } else {
                val progress = listPageScrollProgress(scrollY)
                binding.toolbar.background.alpha = (255 * progress).toInt()
            }

            val progress = listPageScrollProgress(scrollY)
            binding.toolbar.background.alpha = (255 * progress).toInt()
        }
    }

    private fun isGridTabSelected() = true
}