package com.flowfoundation.wallet.page.wallet.presenter

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.reactnative.ReactNativeActivity
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.reactnative.bridge.RNBridge
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutWalletCoordinatorHeaderBinding
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.notification.WalletNotificationManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.manager.walletconnect.getWalletConnectPendingRequests
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.main.widget.CopyCOAAddressDialog
import com.flowfoundation.wallet.page.notification.model.DisplayType
import com.flowfoundation.wallet.page.notification.model.Priority
import com.flowfoundation.wallet.page.notification.model.Type
import com.flowfoundation.wallet.page.notification.model.WalletNotification
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.model.PendingRequestModel
import com.flowfoundation.wallet.page.token.addtoken.AddTokenActivity
import com.flowfoundation.wallet.page.token.custom.AddCustomTokenActivity
import com.flowfoundation.wallet.page.token.manage.ManageTokenActivity
import com.flowfoundation.wallet.page.wallet.WalletFragmentViewModel
import com.flowfoundation.wallet.page.wallet.dialog.SwapDialog
import com.flowfoundation.wallet.page.wallet.model.WalletHeaderModel
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.wallet.toAddress
import com.google.gson.Gson
import java.util.Date

class WalletHeaderPresenter(
    private val fragment: Fragment,
    private val view: View,
) : BaseViewHolder(view), BasePresenter<WalletHeaderModel?> {

    private val binding by lazy { LayoutWalletCoordinatorHeaderBinding.bind(view) }

    private val viewModel by lazy { ViewModelProvider(fragment)[WalletFragmentViewModel::class.java] }

    private val activity by lazy { findActivity(view) as? FragmentActivity }

    @SuppressLint("SetTextI18n")
    override fun bind(model: WalletHeaderModel?) {
        binding.root.setVisible(model != null)
        model ?: return

        with(binding) {
            uiScope {
                val isHideBalance = isHideWalletBalance()
                tvBalance.diffSetText(
                    if (isHideBalance) "****" else model.balance.formatPrice(
                        includeSymbol = true
                    ).ifEmpty { "0" }
                )
                ivHide.setImageResource(if (isHideBalance) R.drawable.ic_eye_off else R.drawable.ic_eye_on)
            }

            val count = if (model.coinCount > 0 ) model.coinCount else FungibleTokenListManager.getCurrentDisplayTokenListSnapshot().size
            tvTokenCount.text = view.context.getString(R.string.token_count, count)

            cvSend.setOnClickListener {
                // Launch React Native Demo Activity instead of TransactionSendActivity
                ReactNativeActivity.launch(view.context, RNBridge.ScreenType.SEND_ASSET)
            }
            cvReceive.setOnClickListener { ReactNativeActivity.launch(view.context, RNBridge.ScreenType.RECEIVE) }
            val address = shortenEVMString(WalletManager.selectedWalletAddress().toAddress())
            tvAddress.text = address
            ivCopy.setVisible(address.isNotBlank())
            ivCopy.setOnClickListener {
                copyAddress(
                    WalletManager.selectedWalletAddress().toAddress()
                )
            }
            if (WalletManager.isChildAccountSelected()) {
                cvSwap.gone()
                cvBuy.gone()
                flManageToken.gone()
                flAddToken.gone()
            } else {
                flAddToken.setOnClickListener {
                    if (WalletManager.isEVMAccountSelected()) {
                        AddCustomTokenActivity.launch(view.context)
                    } else {
                        AddTokenActivity.launch(view.context)
                    }
                }
                cvSwap.setOnClickListener {
                    activity?.let {
                        val url = if (WalletManager.isEVMAccountSelected()) {
                            "https://swap.flow.com/"
                        } else {
                            "https://${if (isTestnet()) "demo" else "app"}" +
                              ".increment.fi/swap"
                        }
                        openBrowser(it, url)
                    }
                }
                flManageToken.setOnClickListener {
                    ManageTokenActivity.launch(view.context)
                }
                cvBuy.setOnClickListener { activity?.let { SwapDialog.show(it.supportFragmentManager) } }
                cvBuy.setVisible(WalletManager.isEVMAccountSelected().not() && AppConfig.isInAppBuy())
                cvSwap.setVisible(AppConfig.isInAppSwap())
                flAddToken.visible()
            }

            ivHide.setOnClickListener {
                uiScope {
                    setHideWalletBalance(!isHideWalletBalance())
                    bind(model)
                    viewModel.onBalanceHideStateUpdate()
                }
            }

            bindPendingRequest()
        }
    }

    private fun copyAddress(text: String) {
        if (EVMWalletManager.isEVMWalletAddress(text)) {
            CopyCOAAddressDialog(view.context, text).show()
            return
        }
        textToClipboard(text)
        toast(R.string.copy_address_toast)
    }

    private fun bindPendingRequest() {
        logd("notification", "bindPendingRequest")

        ioScope {
            val sessions = WalletConnect.get().sessions()
            val requests = getWalletConnectPendingRequests().mapNotNull { request ->
                val metadata = sessions.firstOrNull { request.topic == it.topic }?.metaData
                if (metadata != null) {
                    PendingRequestModel(
                        request = request,
                        metadata = metadata
                    )
                } else null
            }
            logd("notification", "pendingRequest::requests=${Gson().toJson(requests)}")

            requests.firstOrNull()?.let { pendingRequestModel ->
                logd("notification", "pendingRequest::$pendingRequestModel")
                if (WalletNotificationManager.alreadyExist(pendingRequestModel.request.request.id.toString())) {
                    return@ioScope
                }

                val title = if (pendingRequestModel.metadata?.name.isNullOrEmpty()) {
                    R.string.pending_request.res2String()
                } else {
                    "${R.string.pending_request.res2String()} - ${pendingRequestModel.metadata.name}"
                }

                val body = if (pendingRequestModel.metadata?.url.isNullOrEmpty()) {
                    R.string.view_more.res2String()
                } else {
                    view.context.getString(R.string.pending_request_from, pendingRequestModel.metadata.url)
                }

                WalletNotificationManager.addNotification(
                    WalletNotification(
                        id = pendingRequestModel.request.request.id.toString(),
                        icon = pendingRequestModel.metadata?.icons?.firstOrNull(),
                        title = title,
                        body = body,
                        priority = Priority.URGENT,
                        type = Type.PENDING_REQUEST,
                        expiryTime = Date(),
                        displayType = DisplayType.CLICK,
                        conditions = emptyList(),
                        pendingRequest = pendingRequestModel.request // Store the request directly
                    )
                )
            }
        }
    }

    private fun TextView.diffSetText(text: String?) {
        if (text != this.text.toString()) {
            this.text = text
        }
    }
}
