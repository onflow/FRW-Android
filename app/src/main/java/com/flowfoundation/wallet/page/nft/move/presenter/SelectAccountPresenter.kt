package com.flowfoundation.wallet.page.nft.move.presenter

import android.view.View
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemSelectAccountListBinding
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.setVisible

class SelectAccountPresenter(
    private val view: View,
    private val selectedAddress: String,
    private val callback: (String) -> Unit
): BaseViewHolder(view), BasePresenter<String> {

    private val binding by lazy {
        ItemSelectAccountListBinding.bind(view)
    }

    override fun bind(address: String) {
        with(binding) {
            tvAddress.text = address

            if (WalletManager.childAccount(address) != null) {
                val childAccount = WalletManager.childAccount(address)!!
                viewAvatar.setAvatarInfo(iconUrl = childAccount.icon)
                tvName.text = childAccount.name
                tvEvmLabel.gone()
            } else {
                val emoji = AccountEmojiManager.getEmojiByAddress(address)
                viewAvatar.setAvatarInfo(emojiInfo = emoji)
                tvName.text = emoji.emojiName
                tvEvmLabel.setVisible(EVMWalletManager.isEVMWalletAddress(address))
            }
            checkedView.setVisible(selectedAddress == address)
            view.setOnClickListener {
                callback.invoke(address)
            }
        }
    }
}