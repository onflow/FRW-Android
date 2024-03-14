package com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.presenter

import android.view.View
import com.bumptech.glide.Glide
import com.walletconnect.sign.client.Sign
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemWalletConnectSessionBinding
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.sessiondetail.WalletConnectSessionDetailActivity
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.sessiondetail.model.WalletConnectSessionDetailModel
import com.flowfoundation.wallet.utils.extensions.urlHost

class WalletConnectSessionItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<Sign.Model.Session> {

    private val binding by lazy { ItemWalletConnectSessionBinding.bind(view) }

    override fun bind(model: Sign.Model.Session) {
        val meta = model.metaData ?: return
        with(binding) {
            Glide.with(coverView).load(meta.icons.firstOrNull()).into(coverView)
            titleView.text = meta.name
            descView.text = meta.url.urlHost()
        }
        view.setOnClickListener {
            model.toDetailModel()?.let { WalletConnectSessionDetailActivity.launch(view.context, it) }
        }
    }

    private fun Sign.Model.Session.toDetailModel(): WalletConnectSessionDetailModel? {
        val accounts = namespaces[namespaces.keys.firstOrNull()]?.accounts?.firstOrNull()?.split(":") ?: return null
        return WalletConnectSessionDetailModel(
            topic = topic,
            icon = metaData?.icons?.firstOrNull().orEmpty(),
            name = metaData?.name.orEmpty(),
            url = metaData?.url.orEmpty(),
            expiry = this.expiry,
            network = accounts[1],
            address = accounts.last(),
        )
    }
}