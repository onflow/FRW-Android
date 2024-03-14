package com.flowfoundation.wallet.page.guide.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class GuideItemModel(
    @DrawableRes val cover: Int,
    @StringRes val title: Int,
    @StringRes val desc: Int,
)