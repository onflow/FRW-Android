package com.flowfoundation.wallet.page.send.transaction.subpage.amount.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.network.model.AddressBookContact
import kotlinx.parcelize.Parcelize

@Parcelize
class TransactionModel(
    @SerializedName("amount")
    val amount: Float,
    @SerializedName("coinSymbol")
    val coinSymbol: String = FlowCoin.SYMBOL_FLOW,
    @SerializedName("target")
    val target: AddressBookContact,
    @SerializedName("fromAddress")
    val fromAddress: String,
) : Parcelable