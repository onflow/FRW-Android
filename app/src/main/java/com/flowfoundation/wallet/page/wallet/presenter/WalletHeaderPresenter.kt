package com.flowfoundation.wallet.page.wallet.presenter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.transition.TransitionManager
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.LayoutWalletHeaderBinding
import com.flowfoundation.wallet.manager.app.isMainnet
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.coin.TokenStateManager
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletconnect.getWalletConnectPendingRequests
import com.flowfoundation.wallet.page.browser.openBrowser
import com.flowfoundation.wallet.page.profile.subpage.walletconnect.session.WalletConnectSessionActivity
import com.flowfoundation.wallet.page.receive.ReceiveActivity
import com.flowfoundation.wallet.page.send.transaction.TransactionSendActivity
import com.flowfoundation.wallet.page.staking.openStakingPage
import com.flowfoundation.wallet.page.swap.SwapActivity
import com.flowfoundation.wallet.page.token.addtoken.AddTokenActivity
import com.flowfoundation.wallet.page.transaction.record.TransactionRecordActivity
import com.flowfoundation.wallet.page.wallet.WalletFragmentViewModel
import com.flowfoundation.wallet.page.wallet.dialog.SwapDialog
import com.flowfoundation.wallet.page.wallet.model.WalletHeaderModel
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.wallet.toAddress
import jp.wasabeef.glide.transformations.BlurTransformation

class WalletHeaderPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<WalletHeaderModel?> {

    private val binding by lazy { LayoutWalletHeaderBinding.bind(view) }

    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[WalletFragmentViewModel::class.java] }

    private val activity by lazy { findActivity(view) as? FragmentActivity }

    init {
        binding.stackingButton.setVisible(isMainnet())
    }

    @SuppressLint("SetTextI18n")
    override fun bind(model: WalletHeaderModel?) {
        binding.root.setVisible(model != null)
        model ?: return

        with(binding) {
            walletName.text = if (WalletManager.isChildAccountSelected()) {
                WalletManager.childAccount(WalletManager.selectedWalletAddress())?.name ?: R.string.default_child_account_name.res2String()
            } else R.string.wallet.res2String()

            uiScope {
                val isHideBalance = isHideWalletBalance()
                address.diffSetText(if (isHideBalance) "******************" else WalletManager.selectedWalletAddress().toAddress())
                balanceNum.diffSetText(if (isHideBalance) "****" else model.balance.formatPrice(includeSymbol = true))
                hideButton.setImageResource(if (isHideBalance) R.drawable.ic_eye_off else R.drawable.ic_eye_on)
            }

            val count = FlowCoinListManager.coinList().count { TokenStateManager.isTokenAdded(it.address()) }
            coinCountView.text = view.context.getString(R.string.coins_count, count)

            sendButton.setOnClickListener { TransactionSendActivity.launch(view.context) }
            receiveButton.setOnClickListener { ReceiveActivity.launch(view.context) }
            copyButton.setOnClickListener { copyAddress(address.text.toString()) }
            addButton.setOnClickListener { AddTokenActivity.launch(view.context) }
            swapButton.setOnClickListener {
                if (AppConfig.isInAppSwap()) {
                    SwapActivity.launch(view.context)
                } else {
                    activity?.let {
                        openBrowser(it, "https://${if (isTestnet()) "demo" else "app"}.increment.fi/swap")
                    }
                }
            }
            stackingButton.setOnClickListener { openStakingPage(view.context) }
            buyButton.setOnClickListener { activity?.let { SwapDialog.show(it.supportFragmentManager) } }

            sendButton.isEnabled = !WalletManager.isChildAccountSelected()
            swapButton.isEnabled = !WalletManager.isChildAccountSelected()
            stackingButton.isEnabled = !WalletManager.isChildAccountSelected()
            buyButton.isEnabled = !WalletManager.isChildAccountSelected()

            hideButton.setOnClickListener {
                uiScope {
                    setHideWalletBalance(!isHideWalletBalance())
                    bind(model)
                    viewModel.onBalanceHideStateUpdate()
                }
            }

            setupChildAccountCover()

            bindTransactionCount(model.transactionCount)
            bindDomain(model.walletList.username)
            bindPendingRequest()
        }
    }

    private fun bindTransactionCount(transactionCount: Int?) {
        val count = transactionCount ?: 0
        TransitionManager.beginDelayedTransition(binding.root)
        binding.transactionCountWrapper.setVisible(count > 0)
//        binding.transactionCountView.text = "$transactionCount"
        binding.transactionCountWrapper.setOnClickListener { TransactionRecordActivity.launch(view.context) }
    }

    private fun copyAddress(text: String) {
        textToClipboard(text)
        Toast.makeText(view.context, R.string.copy_address_toast.res2String(), Toast.LENGTH_SHORT).show()
    }

    private fun bindPendingRequest() {
        ioScope {
            val requests = getWalletConnectPendingRequests()
            uiScope {
                binding.pendingRequestWrapper.setVisible(requests.isNotEmpty())
                binding.pendingCountView.text = "${requests.size}"
                binding.pendingRequestWrapper.setOnClickListener { WalletConnectSessionActivity.launch(view.context) }
            }
        }
    }

    private fun TextView.diffSetText(text: String?) {
        if (text != this.text.toString()) {
            this.text = text
        }
    }

    private fun bindDomain(username: String) {
        uiScope {
            binding.domainWrapper.setVisible(isMeowDomainClaimed())
            binding.domainView.text = "$username.meow"
//          todo hide domain info for rebranding
            binding.domainWrapper.gone()
        }
    }

    private fun LayoutWalletHeaderBinding.setupChildAccountCover() {
        val isChildAccountSelected = WalletManager.isChildAccountSelected()
        childAccountIconView.setVisible(isChildAccountSelected)
        childAccountMaskView.setVisible(isChildAccountSelected)
        if (isChildAccountSelected) {
            val childAccount = WalletManager.childAccount(WalletManager.selectedWalletAddress())
            if (childAccount?.icon.isNullOrBlank()) {
                childAccountIconView.setVisible(false)
                childAccountMaskView.setVisible(false)
            } else {
                Glide.with(binding.childAccountIconView)
                    .asBitmap()
                    .load(childAccount?.icon)
                    .into(object : SimpleTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            ioScope {
                                val color = Palette.from(resource).generate().getDominantColor(R.color.text_sub.res2color())
                                uiScope {
                                    Glide.with(childAccountIconView)
                                        .load(resource).transform(BlurTransformation(2, 3)).into(childAccountIconView)
                                    childAccountMaskView.backgroundTintList = ColorStateList.valueOf(color)
                                }
                            }
                        }
                    })
            }
        }
    }
}
