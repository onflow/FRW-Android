package com.flowfoundation.wallet.page.inbox.presenter

import android.annotation.SuppressLint
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemInboxNftBinding
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.network.model.InboxNft
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.inbox.InboxViewModel
import com.flowfoundation.wallet.utils.findActivity

class InboxNftItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<InboxNft> {
    private val binding by lazy { ItemInboxNftBinding.bind(view) }
    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[InboxViewModel::class.java] }

    @SuppressLint("SetTextI18n")
    override fun bind(model: InboxNft) {
        val collection = NftCollectionConfig.get(model.collectionAddress) ?: return
        with(binding) {
            titleView.text = collection.name
            tokenIdView.text = "ID: ${model.tokenId}"
            claimButton.setOnClickListener { viewModel.claimNft(model) }
            Glide.with(nftCollectionCoverView).load(collection.logo()).into(nftCollectionCoverView)
            collectionWrapper.setOnClickListener { openBrowser(findActivity(view)!!, collection.officialWebsite) }
        }
    }
}