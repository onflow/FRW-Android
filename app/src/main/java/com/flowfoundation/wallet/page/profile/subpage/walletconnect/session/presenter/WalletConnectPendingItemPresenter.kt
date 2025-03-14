package com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.presenter

import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemWalletConnectSessionBinding
import com.flowfoundation.wallet.manager.walletconnect.dispatch
import com.flowfoundation.wallet.manager.walletconnect.model.toWcRequest
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.model.PendingRequestModel
import com.flowfoundation.wallet.utils.extensions.urlHost
import com.flowfoundation.wallet.utils.ioScope

class WalletConnectPendingItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<PendingRequestModel> {

    private val binding by lazy { ItemWalletConnectSessionBinding.bind(view) }

    override fun bind(model: PendingRequestModel) {
        val meta = model.metadata ?: return
        with(binding) {
            Glide.with(coverView).load(meta.icons.firstOrNull()).into(coverView)
            titleView.text = meta.name
            descView.text = meta.url.urlHost()
        }
        view.setOnClickListener {
            ioScope { model.request.toWcRequest().dispatch() }
        }
    }
}