package com.flowfoundation.wallet.page.send.nft.confirm.presenter

import android.text.Editable
import android.text.TextWatcher
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.cache.recentTransactionCache
import com.flowfoundation.wallet.databinding.DialogSendConfirmBinding
import com.flowfoundation.wallet.manager.account.AccountInfoManager
import com.flowfoundation.wallet.network.model.AddressBookContactBookList
import com.flowfoundation.wallet.page.send.nft.confirm.NftSendConfirmDialog
import com.flowfoundation.wallet.page.send.nft.confirm.NftSendConfirmViewModel
import com.flowfoundation.wallet.page.send.nft.confirm.model.NftSendConfirmDialogModel
import com.flowfoundation.wallet.page.send.transaction.subpage.bindNft
import com.flowfoundation.wallet.page.send.transaction.subpage.bindUserInfo
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.main.HomeTab
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.findActivity

class NftSendConfirmPresenter(
    private val fragment: NftSendConfirmDialog,
    private val binding: DialogSendConfirmBinding,
) : BasePresenter<NftSendConfirmDialogModel> {

    private val viewModel by lazy { ViewModelProvider(fragment)[NftSendConfirmViewModel::class.java] }

    private val sendModel by lazy { viewModel.sendModel }
    private val contact by lazy { viewModel.sendModel.target }

    init {
        binding.sendButton.button().setOnProcessing { 
            // Start the send transaction and wait for completion
            viewModel.send()
        }
        binding.nftWrapper.setVisible()
        binding.titleView.setText(R.string.send_nft)
    }

    override fun bind(model: NftSendConfirmDialogModel) {
        model.userInfo?.let {
            binding.bindUserInfo(sendModel.fromAddress, contact)
            binding.bindNft(sendModel.nft)
            uiScope {
                binding.storageTip.setInsufficientTip(
                    AccountInfoManager.validateOtherTransaction(false)
                )
            }

            if (sendModel.nft.isERC1155NFT()) {
                binding.clNftAmount.visible()
                binding.etNftAmount.setText(viewModel.getCurrentAmount().toString())
                setupAmountHandler()
            } else {
                binding.clNftAmount.gone()
            }
        }
        model.isSendSuccess?.let { updateSendState(it) }
    }

    private fun setupAmountHandler() {

        with(binding) {
            ivAmountPlus.setOnClickListener {
                etNftAmount.setText(viewModel.increaseAmount().toString())
                etNftAmount.setSelection(etNftAmount.text.length)
            }

            ivAmountMinus.setOnClickListener {
                etNftAmount.setText(viewModel.decreaseAmount().toString())
                etNftAmount.setSelection(etNftAmount.text.length)
            }

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val inputAmount = s.toString().toIntOrNull() ?: 0
                    if (s.toString().isNotEmpty() && inputAmount != viewModel.getCurrentAmount()) {
                        val validAmount = viewModel.setAmount(inputAmount)
                        if (validAmount.toString() != s.toString()) {
                            etNftAmount.removeTextChangedListener(this)
                            etNftAmount.setText(validAmount.toString())
                            etNftAmount.setSelection(etNftAmount.text.length)
                            etNftAmount.addTextChangedListener(this)
                        }
                    }
                }
            }
            etNftAmount.addTextChangedListener(textWatcher)
        }
    }

    private fun updateSendState(isSuccess: Boolean) {
        // Dismiss dialog immediately upon TX submission (success or failure)
        fragment.dismissAllowingStateLoss()
        
        // Navigate back to the main NFTs tab
        val activity = findActivity(binding.root)
        if (activity != null) {
            MainActivity.launch(activity, HomeTab.NFT)
        }
        
        if (isSuccess) {
            ioScope {
                val recentCache = recentTransactionCache().read() ?: AddressBookContactBookList(emptyList())
                val list = recentCache.contacts.orEmpty().toMutableList()
                list.removeAll { it.address == sendModel.target.address }
                list.add(0, sendModel.target)
                recentCache.contacts = list
                recentTransactionCache().cache(recentCache)
            }
        }
    }
}