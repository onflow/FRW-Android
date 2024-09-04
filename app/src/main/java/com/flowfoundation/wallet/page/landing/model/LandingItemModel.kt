package com.flowfoundation.wallet.page.landing.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.gson.annotations.SerializedName

data class LandingItemModel(
    @DrawableRes
    @SerializedName("logo")
    val logo: Int,
    @StringRes
    @SerializedName("title")
    val title: Int,
    @StringRes
    @SerializedName("desc")
    val desc: Int
)
