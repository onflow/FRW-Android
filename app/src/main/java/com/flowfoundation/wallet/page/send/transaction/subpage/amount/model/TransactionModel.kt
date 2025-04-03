package com.flowfoundation.wallet.page.send.transaction.subpage.amount.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.network.model.AddressBookContact
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
class TransactionModel(
    @SerializedName("amount")
    val amount: BigDecimal,
    @SerializedName("coinId")
    val coinId: String = "",
    @SerializedName("target")
    val target: AddressBookContact,
    @SerializedName("fromAddress")
    val fromAddress: String,
) : Parcelable