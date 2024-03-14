package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.nft.nftlist.NftViewModel
import com.flowfoundation.wallet.page.nft.nftlist.adapter.CollectionTabsAdapter
import com.flowfoundation.wallet.page.nft.nftlist.findParentAppBarLayout
import com.flowfoundation.wallet.page.nft.nftlist.model.CollectionTabsModel
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class CollectionTabsPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<CollectionTabsModel> {
    private val recyclerView by lazy { view as RecyclerView }

    private val activity by lazy { findActivity(view) as FragmentActivity }
    private val viewModel by lazy { ViewModelProvider(activity)[NftViewModel::class.java] }

    private val adapter by lazy { CollectionTabsAdapter() }

    init {
        with(recyclerView) {
            adapter = this@CollectionTabsPresenter.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 12.dp2px().toInt(), LinearLayout.HORIZONTAL))
        }

        viewModel.collectionTabChangeLiveData.observe(activity) { contractName ->
            if (view.isShown) {
                val data = adapter.getData().toList().map { it.copy() }.onEach { it.isSelected = it.collection.contractName == contractName }
                adapter.setNewDiffData(data)
            }
        }

    }

    override fun bind(model: CollectionTabsModel) {
        model.collections?.let { adapter.setNewDiffData(it) }
        model.isExpand?.let {
            (view.parent as View).setVisible(it)
            view.setVisible(it)
            findParentAppBarLayout(view)?.requestLayout()
        }
    }
}