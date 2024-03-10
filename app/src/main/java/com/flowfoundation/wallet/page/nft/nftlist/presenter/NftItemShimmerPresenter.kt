package com.flowfoundation.wallet.page.nft.nftlist.presenter

import android.view.View
import com.facebook.shimmer.ShimmerFrameLayout
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.page.nft.nftlist.model.NftItemShimmerModel

class NftItemShimmerPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<NftItemShimmerModel> {

    override fun bind(model: NftItemShimmerModel) {
        (view as? ShimmerFrameLayout)?.startShimmer()
    }
}