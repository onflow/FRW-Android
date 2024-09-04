package com.flowfoundation.wallet.page.profile.subpage.currency.model

import com.google.gson.annotations.SerializedName

class CurrencyModel(
    @SerializedName("data")
    val data: List<CurrencyItemModel>? = null,
)

data class CurrencyItemModel(
    @SerializedName("currency")
    val currency: Currency,
    @SerializedName("isSelected")
    val isSelected: Boolean,
)