package com.flowfoundation.wallet.page.send.transaction.subpage

import android.annotation.SuppressLint
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogSendConfirmBinding
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.network.model.AddressBookDomain
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.page.nft.nftlist.getNFTCover
import com.flowfoundation.wallet.page.nft.nftlist.name
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.shortenEVMString

@SuppressLint("SetTextI18n")
fun DialogSendConfirmBinding.bindUserInfo(fromAddress: String, contact: AddressBookContact) {
    fromAddressView.text = "(${shortenEVMString(fromAddress)})"
    if (WalletManager.isChildAccount(fromAddress)) {
        val childAccount = WalletManager.childAccount(fromAddress)
        fromAvatarView.setAvatarInfo(iconUrl = childAccount?.icon)
        fromNameView.text = childAccount?.name ?: R.string.linked_account.res2String()
    } else {
        val emojiInfo = AccountEmojiManager.getEmojiByAddress(fromAddress)
        fromAvatarView.setAvatarInfo(emojiInfo = emojiInfo)
        fromNameView.text = emojiInfo.emojiName
    }
    val toAddress = contact.address ?: ""
    toNameView.text = contact.name()
    if (WalletManager.isChildAccount(toAddress)) {
        val childAccount = WalletManager.childAccount(toAddress)
        toAvatarView.setAvatarInfo(iconUrl = childAccount?.icon)
        namePrefixView.gone()
    } else if (toAddress == WalletManager.wallet()?.walletAddress() || EVMWalletManager.isEVMWalletAddress(toAddress)) {
        val emojiInfo = AccountEmojiManager.getEmojiByAddress(toAddress)
        toAvatarView.setAvatarInfo(emojiInfo = emojiInfo)
        namePrefixView.gone()
    } else {
        namePrefixView.text = contact.prefixName()
        namePrefixView.setVisible(contact.prefixName().isNotEmpty())
        if ((contact.domain?.domainType ?: 0) == 0) {
            if (contact.avatar.isNullOrEmpty().not()) {
                toAvatarView.setAvatarInfo(iconUrl = contact.avatar)
                namePrefixView.gone()
            }
        } else {
            val avatar =
                if (contact.domain?.domainType == AddressBookDomain.DOMAIN_FIND_XYZ) R.drawable.ic_domain_logo_findxyz else R.drawable.ic_domain_logo_flowns
            toAvatarView.setVisible(true)
            toAvatarView.setAvatarIcon(iconId = avatar)
            namePrefixView.gone()
        }
    }

    toAddressView.text = "(${shortenEVMString(contact.address)})"
}

fun DialogSendConfirmBinding.bindNft(nft: Nft) {
    val config = NftCollectionConfig.get(nft.collectionAddress, nft.contractName())
    Glide.with(nftCover).load(nft.getNFTCover()).into(nftCover)
    nftName.text = nft.name()
    Glide.with(nftCollectionIcon).load(config?.logo() ?: nft.collectionSquareImage).into(nftCollectionIcon)
    nftCollectionName.text = config?.name ?: nft.collectionName.orEmpty()
    nftCoinTypeIcon.setImageResource(R.drawable.ic_coin_flow)
}