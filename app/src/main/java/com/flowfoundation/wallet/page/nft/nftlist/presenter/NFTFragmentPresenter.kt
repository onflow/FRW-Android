package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.statusBarHeight
import com.flyco.tablayout.listener.OnTabSelectListener
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
import com.flowfoundation.wallet.utils.extensions.res2String
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

            with(refreshLayout) {
                isEnabled = true
                setOnRefreshListener { viewModel.refresh() }
                setColorSchemeColors(R.color.colorSecondary.res2color())
            }

            binding.viewToggleButton.setOnClickListener { view ->
                showPopupMenu(view)
            }

//            viewModel.isGridViewLiveData.observe(fragment.viewLifecycleOwner) { isGridView ->
//                binding.viewToggleButton.setImageResource(
//                    if (isGridView) R.drawable.ic_nft_tab else R.drawable.ic_change_nft_id
//                )
//            }
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

    private fun showPopupMenu(view: View) {
        val popupMenu = PopupMenu(fragment.requireContext(), view)
        popupMenu.menuInflater.inflate(R.menu.view_toggle_menu, popupMenu.menu)

        val isGridView = viewModel.isGridViewLiveData.value ?: false

        // Change icons dynamically
        val menuListView = popupMenu.menu.findItem(R.id.menu_list_view)
        val menuGridView = popupMenu.menu.findItem(R.id.menu_grid_view)

        menuListView.setIcon(R.drawable.ic_list_view)
        menuGridView.setIcon(R.drawable.ic_grid_view)

        // Check the current selection
        menuListView.isChecked = !isGridView
        menuGridView.isChecked = isGridView

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_list_view -> {
                    viewModel.toggleViewType(false) // Switch to List View
                    Log.d("NFT_GRID", "showPopupMenu:  false")
                    true
                }
                R.id.menu_grid_view -> {
                    viewModel.toggleViewType(true) // Switch to Grid View
                    Log.d("NFT_GRID", "showPopupMenu:  true")
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
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