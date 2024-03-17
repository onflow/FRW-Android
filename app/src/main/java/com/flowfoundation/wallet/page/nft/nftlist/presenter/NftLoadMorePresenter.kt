package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.collection.CollectionActivity
import com.flowfoundation.wallet.page.collection.CollectionViewModel
import com.flowfoundation.wallet.page.nft.nftlist.NftViewModel
import com.flowfoundation.wallet.page.nft.nftlist.model.NftLoadMoreModel
import com.flowfoundation.wallet.utils.findActivity

class NftLoadMorePresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<NftLoadMoreModel> {

    override fun bind(model: NftLoadMoreModel) {
        if (model.isGridLoadMore == true) requestGridNextPage()
        if (model.isListLoadMore == true) requestListNextPage()
    }

    private fun viewModel(): ViewModel? {
        val activity = findActivity(view) ?: return null
        return if (activity.javaClass == CollectionActivity::class.java) {
            ViewModelProvider(activity as FragmentActivity)[CollectionViewModel::class.java]
        } else {
            ViewModelProvider(activity as FragmentActivity)[NftViewModel::class.java]
        }
    }

    private fun requestListNextPage() {
        (viewModel() as? CollectionViewModel)?.requestListNextPage()
        (viewModel() as? NftViewModel)?.requestListNextPage()
    }

    private fun requestGridNextPage() {
        (viewModel() as? NftViewModel)?.requestGridNextPage()
    }
}