package com.flowfoundation.wallet.page.nft.nftlist.model

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.res2color

data class NFTTitleModel(
    val isList: Boolean,
    @DrawableRes val icon: Int,
    @ColorInt val iconTint: Int = Color.WHITE,
    val text: String,
    @ColorInt val textColor: Int = R.color.text.res2color(),
)