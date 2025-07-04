package com.flowfoundation.wallet.page.send.transaction.subpage.transaction.presenter

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.cache.recentTransactionCache
import com.flowfoundation.wallet.databinding.DialogSendConfirmBinding
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.manager.token.FungibleTokenListManager
import com.flowfoundation.wallet.network.model.AddressBookContactBookList
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.SendAmountActivity
import com.flowfoundation.wallet.page.send.transaction.subpage.bindUserInfo
import com.flowfoundation.wallet.page.send.transaction.subpage.transaction.TransactionDialog
import com.flowfoundation.wallet.page.send.transaction.subpage.transaction.TransactionViewModel
import com.flowfoundation.wallet.page.send.transaction.subpage.transaction.model.TransactionDialogModel
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.main.HomeTab
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.formatPrice
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.findActivity
import java.math.BigDecimal

class TransactionPresenter(
    private val fragment: TransactionDialog,
    private val binding: DialogSendConfirmBinding,
) : BasePresenter<TransactionDialogModel> {

    private val viewModel by lazy { ViewModelProvider(fragment)[TransactionViewModel::class.java] }

    private val transaction by lazy { viewModel.transaction }

    private val token by lazy { FungibleTokenListManager.getTokenById(transaction.coinId)!! }
    private val contact by lazy { viewModel.transaction.target }

    init {
        binding.sendButton.button().setOnProcessing { viewModel.send(token) }
        binding.amountWrapper.setVisible()
    }

    override fun bind(model: TransactionDialogModel) {
        model.userInfo?.let {
            binding.bindUserInfo(it.address.orEmpty(), contact)
            setupAmount()
        }
        model.amountConvert?.let { updateAmountConvert(it) }
        model.isSendSuccess?.let { updateSendState(it) }
    }

    private fun updateSendState(isSuccess: Boolean) {
        if (isSuccess) {
            // Notify SendAmountActivity that transaction was submitted
            val activity = fragment.activity
            if (activity is SendAmountActivity) {
                activity.onTransactionSubmitted()
                uiScope { fragment.dismissAllowingStateLoss() }
            } else {
                // If not launched from SendAmountActivity, navigate back to wallet tab directly
                uiScope { 
                    fragment.dismissAllowingStateLoss()
                    
                    // Navigate back to the main wallet tab
                    val rootActivity = findActivity(binding.root)
                    if (rootActivity != null) {
                        MainActivity.launch(rootActivity, HomeTab.WALLET)
                    }
                }
            }
            
            ioScope {
                val recentCache = recentTransactionCache().read() ?: AddressBookContactBookList(emptyList())
                val list = recentCache.contacts.orEmpty().toMutableList()
                list.removeAll { it.address == transaction.target.address }
                list.add(0, transaction.target)
                recentCache.contacts = list
                recentTransactionCache().cache(recentCache)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupAmount() {
        with(binding) {
            amountView.text = "${transaction.amount} ${token.symbol.uppercase()}"
                    coinNameView.text = token.name
            Glide.with(coinIconView).load(token.tokenIcon()).into(coinIconView)
            uiScope {
                storageTip.setInsufficientTip(
                    if (token.isFlowToken()) {
                        AccountInfoManager.validateFlowTokenTransaction(transaction.amount, false)
                    } else {
                        AccountInfoManager.validateOtherTransaction(false)
                    }
                )
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateAmountConvert(amountConvert: BigDecimal) {
        binding.amountConvertView.text = "≈ ${amountConvert.formatPrice(includeSymbol = true, includeSymbolSpace = true)}"
    }
}