package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.animation.ArgbEvaluator
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
            addButton.setVisible(WalletManager.isEVMAccountSelected().not() && WalletManager
                .isChildAccountSelected().not())

            with(refreshLayout) {
                isEnabled = true
                setOnRefreshListener { viewModel.refresh() }
                setColorSchemeColors(R.color.colorSecondary.res2color())
            }
        }

        startShimmer(binding.shimmerLayout.shimmerLayout)
    }

    override fun bind(model: NFTFragmentModel) {
        model.favorite?.let {
            isTopSelectionExist = it.isNotEmpty()
            updateToolbarBackground()
        }
        model.onListScrollChange?.let { updateToolbarBackground(it) }
        model.listPageData?.let { binding.refreshLayout.isRefreshing = false }
    }

    private fun listPageScrollProgress(scrollY: Int): Float {
        val scroll = if (scrollY < 0) viewModel.listScrollChangeLiveData.value ?: 0 else scrollY
        val maxScrollY = ScreenUtils.getScreenHeight() * 0.25f
        return min(scroll / maxScrollY, 1f)
    }

    private fun updateToolbarBackground(scrollY: Int = -1) {
        if (!isTopSelectionExist) {
            binding.toolbar.background.alpha = 255
            binding.tabsBackground.background.setTint(R.color.neutrals4.res2color())
        } else {
            val progress = listPageScrollProgress(scrollY)
            binding.toolbar.background.alpha = (255 * progress).toInt()
            binding.tabsBackground.background.setTint(
                ArgbEvaluator().evaluate(
                    progress,
                    R.color.white.res2color(),
                    R.color.neutrals4.res2color()
                ) as Int
            )
        }
    }
}