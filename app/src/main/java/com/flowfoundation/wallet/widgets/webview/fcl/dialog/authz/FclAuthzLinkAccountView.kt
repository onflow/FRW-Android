package com.flowfoundation.wallet.widgets.webview.fcl.dialog.authz

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogLinkAccountBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.browser.loadFavicon
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel

class FclAuthzLinkAccountView : FrameLayout, OnTransactionStateChange {
    private val binding: DialogLinkAccountBinding

    private lateinit var data: FclDialogModel

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
            : super(context, attrs, defStyleAttr)

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_link_account, this, false)
        addView(view)
        binding = DialogLinkAccountBinding.bind(view)
        TransactionStateManager.addOnTransactionStateChange(this)
    }

    fun setup(data: FclDialogModel, approveCallback: ((isApprove: Boolean) -> Unit)) {
        this.data = data
        binding.setup(data)

        binding.root.requestFocus()
        binding.startButton.setOnProcessing {
            showLinkingUI()
            approveCallback.invoke(true)
        }
        showDefaultUI()
    }

    override fun onTransactionStateChange() {
        val transactionState = TransactionStateManager.getTransactionStateList().lastOrNull() ?: return
        if (transactionState.isProcessing()) {
            // ignore
        } else if (transactionState.isFailed()) {
            showFailedUI()
        } else if (transactionState.isSuccess()) {
            showSuccessUI()
        }
    }

    private fun showFailedUI() {
        with(binding) {
            titleView.setText(R.string.link_fail)
            line.setBackgroundResource(R.drawable.bg_link_account_error_line)
            startButton.setVisible(false)
            tryAgainButton.setVisible(true)
            errorXIcon.setVisible(true)
            linkTipsWrapper.setVisible(false)
            point1.setVisible(false)
            point2.setVisible(false)
            tryAgainButton.setOnClickListener { FclAuthzDialog.dismiss(true) }
        }
    }

    private fun showSuccessUI() {
        with(binding) {
            descView.setVisible(true)
            descView.setText(R.string.link_account_success_desc)
            titleView.setText(R.string.successful)
            defaultLayout.setVisible(false)
            linkTipsWrapper.setVisible(false)
            successLayout.setVisible(true)
            successStartButton.setOnClickListener { FclAuthzDialog.dismiss(true) }
            WalletManager.refreshChildAccount()
        }
    }

    private fun showDefaultUI() {
        with(binding) {
            descView.setVisible(false)
            titleView.setText(R.string.link_account)
            line.setBackgroundResource(R.drawable.bg_link_account_line)
            defaultLayout.setVisible()
            startButton.setVisible()
            point1.setVisible()
            point2.setVisible()
            linkTipsWrapper.setVisible()
            successLayout.setVisible(false)
            tryAgainButton.setVisible(false)
            errorXIcon.setVisible(false)
            with(startButton.layoutParams as MarginLayoutParams) {
                topMargin = 18.dp2px().toInt()
                startButton.layoutParams = this
            }
        }
    }

    private fun showLinkingUI() {
        showDefaultUI()
        with(binding) {
            titleView.setText(R.string.account_linking)
            descView.setVisible(false)
            point1.setVisible(false, invisible = true)
            point2.setVisible(false, invisible = true)
            linkTipsWrapper.setVisible(false)
            with(startButton.layoutParams as MarginLayoutParams) {
                topMargin = 70.dp2px().toInt()
                startButton.layoutParams = this
            }
        }
    }
}

private fun DialogLinkAccountBinding.setup(fcl: FclDialogModel) {
    dappIcon.loadFavicon(fcl.logo ?: fcl.url?.toFavIcon())
    dappName.text = fcl.title
    ioScope {
        val userinfo = AccountManager.userInfo()
        uiScope {
            walletIcon.loadAvatar(userinfo?.avatar.orEmpty())
            walletName.text = userinfo?.nickname
        }
    }
}