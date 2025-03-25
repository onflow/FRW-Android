package com.flowfoundation.wallet.page.address.presenter

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.res.ColorStateList
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemAddressBookPersonBinding
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.address.AddressBookActivity
import com.flowfoundation.wallet.page.address.AddressBookViewModel
import com.flowfoundation.wallet.page.address.FlowDomainServer
import com.flowfoundation.wallet.page.address.model.AddressBookPersonModel
import com.flowfoundation.wallet.page.addressadd.AddressAddActivity
import com.flowfoundation.wallet.page.send.transaction.SelectSendAddressViewModel
import com.flowfoundation.wallet.page.send.transaction.TransactionSendActivity
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.wallet.toAddress

class AddressBookPersonPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<AddressBookPersonModel> {

    private val binding by lazy { ItemAddressBookPersonBinding.bind(view) }

    private val viewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[AddressBookViewModel::class.java] }

    private val isSendTransactionPage by lazy { BaseActivity.getCurrentActivity()?.javaClass == TransactionSendActivity::class.java }

    private val isSendNftPage by lazy { BaseActivity.getCurrentActivity()?.javaClass == com.flowfoundation.wallet.page.nft.nftdetail.NftDetailActivity::class.java }

    private val isAddressBookPage by lazy { BaseActivity.getCurrentActivity()?.javaClass == AddressBookActivity::class.java }

    @SuppressLint("SetTextI18n")
    override fun bind(model: AddressBookPersonModel) {
        val data = model.data
        with(binding) {
            nameView.text = data.name()
            val address = data.address?.toAddress().orEmpty()
            if (WalletManager.isChildAccount(address)) {
                val childAccount = WalletManager.childAccount(address)
                val avatar = childAccount?.icon
                avatarView.setVisible(!avatar.isNullOrEmpty(), invisible = true)
                avatarView.loadAvatar(avatar.orEmpty())
                namePrefixView.setVisible(avatar.isNullOrEmpty())
            } else if (address == WalletManager.wallet()?.walletAddress() || EVMWalletManager
                    .isEVMWalletAddress(address)
            ) {
                val emojiInfo =
                    AccountEmojiManager.getEmojiByAddress(WalletManager.wallet()?.walletAddress())
                namePrefixView.text = Emoji.getEmojiById(emojiInfo.emojiId)
                namePrefixView.backgroundTintList =
                    ColorStateList.valueOf(Emoji.getEmojiColorRes(emojiInfo.emojiId))
                namePrefixView.visible()
            } else {
                namePrefixView.text = data.prefixName()
                namePrefixView.setVisible(data.prefixName().isNotEmpty())
                if ((data.domain?.domainType ?: 0) == 0) {
                    avatarView.setVisible(!data.avatar.isNullOrEmpty(), invisible = true)
                    avatarView.loadAvatar(data.avatar.orEmpty())
                    namePrefixView.setVisible(data.avatar.isNullOrEmpty())
                } else {
                    bindDomainAvatar(data.domain?.domainType ?: 0)
                    namePrefixView.gone()
                }
            }

            addressView.text = data.address?.toAddress().orEmpty()

            addButton.setVisible(isAddressBookPage && !viewModel.isAddressBookContains(model))

            addButton.setOnClickListener { viewModel.addFriend(model.data) }
        }

        view.setOnClickListener {


            ViewModelProvider(findActivity(view) as FragmentActivity)[SelectSendAddressViewModel::class.java]
                .onAddressSelectedLiveData.postValue(data)
            when {
                isSendTransactionPage -> {}
                isSendNftPage -> {}
                viewModel.isAddressBookContains(model) -> AddressActionDialog(findActivity(view) as FragmentActivity, data).show()
            }
        }
    }

    private fun bindDomainAvatar(domainType: Int) {
        val avatar = when (domainType) {
            FlowDomainServer.FLOWNS.type -> R.drawable.ic_domain_logo_flowns
            FlowDomainServer.FIND.type -> R.drawable.ic_domain_logo_findxyz
            else -> R.mipmap.ic_launcher_round
        }
        with(binding.avatarView) {
            setVisible(true)
            Glide.with(this).load(avatar).into(this)
        }
    }
}

private class AddressActionDialog(
    private val context: FragmentActivity,
    private val contact: AddressBookContact,
) {

    private val viewModel by lazy { ViewModelProvider(context)[AddressBookViewModel::class.java] }

    fun show() {
        var dialog: Dialog? = null
        with(AlertDialog.Builder(context, R.style.Theme_AlertDialogTheme)) {
            setAdapter(ArrayAdapter<String>(context, R.layout.item_address_book_action).apply {
                if (!contact.contactName.isNullOrBlank()) {
                    add(R.string.edit.res2String())
                }
                add(R.string.delete.res2String())
            }) { dialog, which ->
                dialog.dismiss()
                if (which == 0) AddressAddActivity.launch(context, contact) else viewModel.delete(contact)
            }
            with(create()) {
                dialog = this
                show()
            }
        }
    }
}