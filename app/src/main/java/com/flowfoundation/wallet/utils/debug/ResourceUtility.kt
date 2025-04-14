package com.flowfoundation.wallet.utils.debug

import android.content.res.Resources
import androidx.annotation.ColorRes
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
}
