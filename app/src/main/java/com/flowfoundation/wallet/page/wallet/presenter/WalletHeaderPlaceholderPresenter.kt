package com.flowfoundation.wallet.page.wallet.presenter

import android.view.View
import com.facebook.shimmer.ShimmerFrameLayout
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.utils.extensions.setVisible

class WalletHeaderPlaceholderPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<Boolean> {

    override fun bind(model: Boolean) {
        view.setVisible(model)
        (view as ShimmerFrameLayout).startShimmer()
    }
}