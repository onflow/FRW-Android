package com.flowfoundation.wallet.page.send.nft.confirm.presenter

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
import com.flowfoundation.wallet.utils.extensions.setVisible
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
        binding.sendButton.button().setOnProcessing { viewModel.send() }
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
        }
        model.isSendSuccess?.let { updateSendState(it) }
    }

    private fun updateSendState(isSuccess: Boolean) {
        if (isSuccess) {
            uiScope { 
                fragment.dismissAllowingStateLoss()
                
                // Navigate back to the main NFTs tab
                val activity = findActivity(binding.root)
                if (activity != null) {
                    MainActivity.launch(activity, HomeTab.NFT)
                }
            }
            
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