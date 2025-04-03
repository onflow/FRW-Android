package com.flowfoundation.wallet.page.nft.collectionlist

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil
import com.flowfoundation.wallet.page.address.model.AddressBookPersonModel

val nftCollectionListDiffCallback = object : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is AddressBookPersonModel && newItem is AddressBookPersonModel) {
            return oldItem.data == newItem.data
        }
        return oldItem == newItem
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is AddressBookPersonModel && newItem is AddressBookPersonModel) {
            return oldItem.data == newItem.data && oldItem.isFriend == newItem.isFriend
        }
        return oldItem == newItem
    }
}
