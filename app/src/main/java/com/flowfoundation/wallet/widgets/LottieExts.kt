package com.flowfoundation.wallet.widgets

import androidx.annotation.ColorInt
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback


fun LottieAnimationView.setColorTint(@ColorInt color: Int) {
    addValueCallback(
        KeyPath("**"),
        LottieProperty.COLOR_FILTER,
        LottieValueCallback(SimpleColorFilter(color))
    )
}