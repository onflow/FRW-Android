package com.flowfoundation.wallet.page.notification.presenter

import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemWalletNotificationBinding
import com.flowfoundation.wallet.manager.inbox.InboxManager
import com.flowfoundation.wallet.manager.notification.WalletNotificationManager
import com.flowfoundation.wallet.manager.walletconnect.dispatch
import com.flowfoundation.wallet.manager.walletconnect.getWalletConnectPendingRequests
import com.flowfoundation.wallet.manager.walletconnect.model.toWcRequest
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.notification.model.DisplayType
import com.flowfoundation.wallet.reactnative.ReactNativeActivity
import com.flowfoundation.wallet.page.notification.model.Type
import com.flowfoundation.wallet.page.notification.model.WalletNotification
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.WalletConnectSessionActivity
import com.flowfoundation.wallet.page.wallet.dialog.SwapDialog
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge


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
                if (model.type == Type.INBOX) {
                    ivIcon.setImageResource(R.drawable.ic_inbox)
                } else {
                    Glide.with(ivIcon).load(model.icon()).transform(CenterCrop(), CircleCrop()).into(ivIcon)
                }
                ivIcon.visible()
            }
            tvTitle.text = model.title.orEmpty()
            val content = model.body.orEmpty()
            tvContent.text = if (model.url.isNullOrEmpty()) {
                content
            } else {
                SpannableString(content).apply {
                    setSpan(UnderlineSpan(), 0, content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            if (model.type == Type.IMAGE) {
                model.image?.let {
                    Glide.with(ivImage).load(it)
                        .transition(DrawableTransitionOptions.withCrossFade(100))
                        .into(ivImage)
                }
            }
            if (model.displayType == DisplayType.ONCE) {
                WalletNotificationManager.markAsRead(model.id)
            }

            flClose.setOnClickListener {
                if (model.type == Type.PENDING_REQUEST) {
                    WalletNotificationManager.removeNotification(model)
                    return@setOnClickListener
                }
                if (model.displayType == DisplayType.CLICK) {
                    WalletNotificationManager.markAsRead(model.id)
                }
                WalletNotificationManager.removeNotification(model)
            }
            binding.root.setOnClickListener {
                if (model.type == Type.INBOX) {
                    ReactNativeActivity.launchClaimTokens(view.context)
                    WalletNotificationManager.removeNotification(model)
                    InboxManager.clearNotificationRef()
                    return@setOnClickListener
                }
                if (model.type == Type.PENDING_REQUEST) {
                    logd("NotificationItemPresenter", "click pending request")

                    val request = model.pendingRequest
                    if (request != null) {
                        logd("NotificationItemPresenter", "using cached request: $request")
                        ioScope {
                            try {
                                request.toWcRequest().dispatch()
                                WalletNotificationManager.removeNotification(model)
                                logd("NotificationItemPresenter", "notification removed after successful dispatch")
                            } catch (e: Exception) {
                                loge("NotificationItemPresenter", "failed to dispatch request: ${e.message}")
                                loge(e)
                            }
                        }
                    } else {
                        logd("NotificationItemPresenter", "no cached request, querying current pending requests")
                        val requests = getWalletConnectPendingRequests()
                        logd("NotificationItemPresenter", "pending requests: $requests")
                        val foundRequest = requests.firstOrNull { model.id == it.request.id.toString() }
                        logd("NotificationItemPresenter", "found request: $foundRequest")
                        if (foundRequest != null) {
                            ioScope {
                                try {
                                    foundRequest.toWcRequest().dispatch()
                                    WalletNotificationManager.removeNotification(model)
                                    logd("NotificationItemPresenter", "notification removed after successful dispatch")
                                } catch (e: Exception) {
                                    loge("NotificationItemPresenter", "failed to dispatch request: ${e.message}")
                                    loge(e)
                                }
                            }
                        } else {
                            logd("NotificationItemPresenter", "no matching request found, opening WalletConnect session activity")
                            WalletConnectSessionActivity.launch(view.context)
                        }
                    }
                    return@setOnClickListener
                }
                model.url?.let {
                    val activity = BaseActivity.getCurrentActivity() ?: return@setOnClickListener
                    val uri = Uri.parse(it)
                    if (uri.scheme == "fw") {
                        when (uri.host) {
                            "buyFlow" -> {
                                SwapDialog.show(activity.supportFragmentManager)
                            }
                        }
                    } else {
                        openBrowser(activity, it)
                    }
                }
            }
        }
    }
}
