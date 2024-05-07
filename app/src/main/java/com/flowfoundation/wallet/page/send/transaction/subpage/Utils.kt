package com.flowfoundation.wallet.page.send.transaction.subpage

import android.annotation.SuppressLint
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogSendConfirmBinding
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.network.model.AddressBookDomain
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.page.nft.nftlist.name
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.shortenEVMString
import com.flowfoundation.wallet.wallet.toAddress


@SuppressLint("SetTextI18n")
fun DialogSendConfirmBinding.bindUserInfo(userInfo: UserInfoData, contact: AddressBookContact) {
    fromAvatarView.loadAvatar(userInfo.avatar)
    fromNameView.text = userInfo.nickname
    val fromAddress = userInfo.address?.toAddress()
    fromAddressView.text = "(${shortenEVMString(fromAddress)})"

    toNameView.text = "${contact.name()} ${if (!contact.username.isNullOrEmpty()) " (@${contact.username})" else ""}"
    namePrefixView.text = contact.prefixName()
    namePrefixView.setVisible(contact.prefixName().isNotEmpty())

    if ((contact.domain?.domainType ?: 0) == 0) {
        toAvatarView.setVisible(!contact.avatar.isNullOrEmpty(), invisible = true)
        toAvatarView.loadAvatar(contact.avatar.orEmpty())
        namePrefixView.setVisible(false)
    } else {
        val avatar =
            if (contact.domain?.domainType == AddressBookDomain.DOMAIN_FIND_XYZ) R.drawable.ic_domain_logo_findxyz else R.drawable.ic_domain_logo_flowns
        toAvatarView.setVisible(true)
        Glide.with(toAvatarView).load(avatar).into(toAvatarView)
    }

    toAddressView.text = "(${shortenEVMString(contact.address)})"
}

fun DialogSendConfirmBinding.bindNft(nft: Nft) {
    val config = NftCollectionConfig.get(nft.collectionAddress) ?: return
    Glide.with(nftCover).load(nft.cover()).into(nftCover)
    nftName.text = nft.name()
    Glide.with(nftCollectionIcon).load(config.logo).into(nftCollectionIcon)
    nftCollectionName.text = config.name
    nftCoinTypeIcon.setImageResource(R.drawable.ic_coin_flow)
}