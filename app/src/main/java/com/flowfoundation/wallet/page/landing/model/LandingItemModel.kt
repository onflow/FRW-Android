package com.flowfoundation.wallet.page.landing.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class LandingItemModel(
    @DrawableRes val logo: Int,
    @StringRes val title: Int,
    @StringRes val desc: Int
)
