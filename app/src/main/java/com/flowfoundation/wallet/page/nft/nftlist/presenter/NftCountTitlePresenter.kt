package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemNftCountTitleBinding
import com.flowfoundation.wallet.page.nft.nftlist.model.NFTCountTitleModel

class NftCountTitlePresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<NFTCountTitleModel> {
    private val binding by lazy { ItemNftCountTitleBinding.bind(view) }

    override fun bind(model: NFTCountTitleModel) {
        with(binding) {
            textView.text = view.context.getString(R.string.nft_count, model.count)
        }
    }
}