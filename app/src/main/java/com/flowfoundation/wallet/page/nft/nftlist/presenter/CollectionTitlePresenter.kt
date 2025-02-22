package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemNftListCollectionTitleBinding
import com.flowfoundation.wallet.page.nft.nftlist.model.CollectionTitleModel
import com.flowfoundation.wallet.utils.extensions.setVisible

class CollectionTitlePresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<CollectionTitleModel> {
    private val binding by lazy { ItemNftListCollectionTitleBinding.bind(view) }

    override fun bind(model: CollectionTitleModel) {
        view.setVisible()
        binding.textView.text = view.context.getString(R.string.collections_count, model.count)
    }
}