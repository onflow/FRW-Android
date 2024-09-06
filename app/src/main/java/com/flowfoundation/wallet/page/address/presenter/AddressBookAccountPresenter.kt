package com.flowfoundation.wallet.page.address.presenter

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemAddressBookAccountBinding
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.address.model.AddressBookAccountModel
import com.flowfoundation.wallet.page.send.transaction.SelectSendAddressViewModel
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.shortenEVMString


class AddressBookAccountPresenter(
    private val view: View
): BaseViewHolder(view), BasePresenter<AddressBookAccountModel> {
    private val binding by lazy {
        ItemAddressBookAccountBinding.bind(view)
    }

    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[SelectSendAddressViewModel::class.java] }

    override fun bind(model: AddressBookAccountModel) {
        with(binding) {
            tvAddress.text = shortenEVMString(model.address)

            if (WalletManager.childAccount(model.address) != null) {
                val childAccount = WalletManager.childAccount(model.address)!!
                viewAvatar.setAvatarInfo(iconUrl = childAccount.icon)
                tvName.text = childAccount.name
                tvEvmLabel.gone()
            } else {
                val emoji = AccountEmojiManager.getEmojiByAddress(model.address)
                viewAvatar.setAvatarInfo(emojiInfo = emoji)
                tvName.text = emoji.emojiName
                tvEvmLabel.setVisible(EVMWalletManager.isEVMWalletAddress(model.address))
            }
            view.setOnClickListener {
                viewModel.onAddressSelectedLiveData.postValue(AddressBookContact(
                    address = model.address,
                    username = tvName.text.toString(),
                ))
            }
        }
    }
}