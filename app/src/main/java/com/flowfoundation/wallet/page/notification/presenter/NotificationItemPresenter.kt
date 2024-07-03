package com.flowfoundation.wallet.page.notification.presenter

import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemWalletNotificationBinding
import com.flowfoundation.wallet.manager.notification.WalletNotificationManager
import com.flowfoundation.wallet.page.notification.model.WalletNotification
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.WalletConnectSessionActivity
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.visible


class NotificationItemPresenter(
    private val view: View
) : BaseViewHolder(view), BasePresenter<WalletNotification> {

    private val binding by lazy {
        ItemWalletNotificationBinding.bind(view)
    }

    override fun bind(model: WalletNotification) {
        with(binding) {
            if (model.icon.isNullOrEmpty()) {
                ivIcon.gone()
            } else {
                Glide.with(ivIcon).load(model.icon()).transform(CenterCrop(), CircleCrop())
                    .into(ivIcon)
                ivIcon.visible()
            }
            tvTitle.text = model.title
            tvContent.text = if (model.clickable) {
                SpannableString(model.content).apply {
                    setSpan(UnderlineSpan(), 0, model.content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else model.content

            flClose.setOnClickListener {
                WalletNotificationManager.removeNotification(model)
            }
            binding.root.setOnClickListener {
                when(model.deepLink) {
                    "pending_request" -> WalletConnectSessionActivity.launch(view.context)
                }
            }
        }
    }
}