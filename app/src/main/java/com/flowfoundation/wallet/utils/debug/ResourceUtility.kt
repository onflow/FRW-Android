package com.flowfoundation.wallet.utils.debug

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.flowfoundation.wallet.utils.Env

object ResourceUtility {

    private val applicationContext by lazy {
        Env.getApp()
    }

    private val resources: Resources
        get() {
            return applicationContext.resources
        }

    fun getColor(@ColorRes colorId: Int): Int {
        return resources.getColor(colorId, null)
    }

    fun getString(@StringRes stringId: Int, vararg formatArgs: Any?): String {
        return resources.getString(stringId, *formatArgs)
    }

    fun getQuantityString(@PluralsRes pluralId: Int, quantity: Int): String {
        return resources.getQuantityString(pluralId, quantity, quantity)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    fun getDrawable(@DrawableRes drawableId: Int): Drawable {
        return resources.getDrawable(drawableId, null)
    }

    fun getDimension(@DimenRes dimensionId: Int): Float {
        return resources.getDimension(dimensionId)
    }
}
