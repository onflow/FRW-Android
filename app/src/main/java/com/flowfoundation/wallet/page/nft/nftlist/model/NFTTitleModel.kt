package com.flowfoundation.wallet.page.nft.nftlist.model

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.res2color
import com.google.gson.annotations.SerializedName

data class NFTTitleModel(
    @SerializedName("isList")
    val isList: Boolean,
    @DrawableRes
    @SerializedName("icon")
    val icon: Int,
    @ColorInt
    @SerializedName("iconTint")
    val iconTint: Int = Color.WHITE,
    @SerializedName("text")
    val text: String,
    @ColorInt
    @SerializedName("textColor")
    val textColor: Int = R.color.text.res2color(),
)