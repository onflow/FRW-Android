package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemNftListCollectionLineBinding
import com.flowfoundation.wallet.page.collection.CollectionActivity
import com.flowfoundation.wallet.page.nft.nftlist.model.CollectionItemModel
import com.flowfoundation.wallet.page.profile.subpage.wallet.ChildAccountCollectionManager
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.setVisible

class CollectionLineItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<CollectionItemModel> {
    private val binding by lazy { ItemNftListCollectionLineBinding.bind(view) }

    private val corner by lazy { 12.dp2px().toInt() }

    override fun bind(model: CollectionItemModel) {
        with(binding) {
            val config = model.collection
            nameView.text = config.name
            countView.text = view.context.getString(R.string.collectibles_count, model.count)
            Glide.with(coverView).load(config.logo()).transform(CenterCrop(), RoundedCorners(corner)).into(coverView)
        }
        bindAccessible(model)
        view.setOnClickListener { CollectionActivity.launch(view.context, model.collection.contractName()) }
    }

    private fun bindAccessible(model: CollectionItemModel) {
        val accessible = ChildAccountCollectionManager.isNFTCollectionAccessible(model.collection.contractIdWithCollection())
        binding.countView.setVisible(accessible)
        binding.tvInaccessibleTag.setVisible(accessible.not())
    }
}