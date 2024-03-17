package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemNftListCollectionTitleBinding
import com.flowfoundation.wallet.page.nft.nftlist.NftViewModel
import com.flowfoundation.wallet.page.nft.nftlist.model.CollectionTitleModel
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity

class CollectionTitlePresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<CollectionTitleModel> {
    private val binding by lazy { ItemNftListCollectionTitleBinding.bind(view) }

    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[NftViewModel::class.java] }

    init {
        binding.toggleView.setOnClickListener { viewModel.toggleCollectionExpand() }
    }

    override fun bind(model: CollectionTitleModel) {
        view.setVisible()
        binding.textView.text = view.context.getString(R.string.collections_count, model.count)
    }
}