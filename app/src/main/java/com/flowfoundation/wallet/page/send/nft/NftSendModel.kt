package com.flowfoundation.wallet.page.send.nft

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.network.model.Nft
import kotlinx.parcelize.Parcelize

@Parcelize
class NftSendModel(
    @SerializedName("nft")
    val nft: Nft,
    @SerializedName("target")
    val target: AddressBookContact,
    @SerializedName("fromAddress")
    val fromAddress: String,
) : Parcelable