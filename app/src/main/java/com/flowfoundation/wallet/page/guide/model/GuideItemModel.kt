package com.flowfoundation.wallet.page.guide.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.gson.annotations.SerializedName

data class GuideItemModel(
    @DrawableRes
    @SerializedName("cover")
    val cover: Int,
    @StringRes
    @SerializedName("title")
    val title: Int,
    @StringRes
    @SerializedName("desc")
    val desc: Int,
)