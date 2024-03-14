package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemNftListTitleBinding
import com.flowfoundation.wallet.page.nft.nftlist.model.NFTTitleModel
import com.flowfoundation.wallet.utils.extensions.res2pix

class TitleItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<NFTTitleModel> {
    private val binding by lazy { ItemNftListTitleBinding.bind(view) }

    override fun bind(model: NFTTitleModel) {
        with(binding) {
            iconView.setImageResource(model.icon)
            iconView.setColorFilter(model.iconTint)

            textView.text = model.text
            textView.setTextColor(model.textColor)
        }
        with(view) {
            setPadding(R.dimen.nft_list_divider_size.res2pix(), paddingTop, paddingRight, paddingBottom)
        }
    }
}