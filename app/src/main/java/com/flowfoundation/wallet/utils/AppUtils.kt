package com.flowfoundation.wallet.utils

import android.app.Activity
import android.content.res.Configuration
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.firebase.storage.firebaseImage

fun isNightMode(activity: Activity? = null): Boolean {
    val context = activity ?: BaseActivity.getCurrentActivity() ?: Env.getApp()
    return context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}

fun String?.parseAvatarUrl(): String {
    if (this.isNullOrEmpty()) {
        return ""
    }
    val url = this.firebaseImage()
    return url.parseBoringAvatar()
}

fun isDev() = BuildConfig.APPLICATION_ID.contains("dev")

fun isTesting() = BuildConfig.DEBUG