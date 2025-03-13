package com.flowfoundation.wallet.page.nft.search.presenter

import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityNftSearchBinding
import com.flowfoundation.wallet.page.nft.nftlist.adapter.NFTListAdapter
import com.flowfoundation.wallet.page.nft.nftlist.model.NFTItemModel
import com.flowfoundation.wallet.page.nft.search.NFTSearchActivity
import com.flowfoundation.wallet.page.nft.search.model.NFTListType
import com.flowfoundation.wallet.page.nft.search.viewmodel.NFTItemListViewModel
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2dip
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.widgets.itemdecoration.GridLayoutItemDecoration

class NFTSearchPresenter(
    private val activity: NFTSearchActivity,
    private val binding: ActivityNftSearchBinding
): BasePresenter<List<NFTItemModel>?> {

    private val viewModel by lazy { ViewModelProvider(activity)[NFTItemListViewModel::class.java]}
    private val adapter by lazy {
        NFTListAdapter()
    }
    private val dividerSize by lazy { R.dimen.nft_list_divider_size.res2dip().toDouble() }
    init {
        with(binding.rvNftList) {
            adapter = this@NFTSearchPresenter.adapter
            layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (this@NFTSearchPresenter.adapter.isSingleLineItem(position)) spanCount else 1
                    }
                }
            }
            addItemDecoration(
                GridLayoutItemDecoration(vertical = dividerSize, horizontal = dividerSize)
            )
        }
        binding.errorLayout.setOnRefreshClickListener {
            viewModel.retry()
        }
        binding.tvCancel.setOnClickListener {
            activity.finish()
        }
        binding.searchLayout.setOnSearchListener {
            viewModel.searchNFT(it)
        }
    }

    override fun bind(model: List<NFTItemModel>?) {
        with(binding) {
            loadingLayout.gone()
            if (model == null) {
                tvListCount.text = ""
                errorLayout.visible()
                adapter.setNewDiffData(emptyList())
                return
            }
            tvListCount.text = activity.getString(R.string.nft_count, model.size)
            adapter.setNewDiffData(model)
            if (adapter.itemCount > 0) {
                rvNftList.scrollToPosition(0)
            }
        }
    }

    fun configureLoadingState(loading: Boolean) {
        with(binding) {
            if (loading) {
                adapter.setNewDiffData(emptyList())
                errorLayout.gone()
                loadingLayout.visible()
            } else {
                loadingLayout.gone()
            }
        }
    }

    fun updateLoadingProgress(current: Int, total: Int) {
        binding.loadingLayout.setLoadingProgress(current, total)
    }

    fun updateListType(type: NFTListType) {
        binding.tvListType.text = type.resId.res2String()
    }
}