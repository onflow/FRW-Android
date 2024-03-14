package com.flowfoundation.wallet.utils.extensions

import android.app.Activity
import android.content.Context
import android.view.View

fun Context?.isActivityVisible(): Boolean {
    val activity = this as? Activity ?: return false
    return !activity.isDestroyed && activity.window.decorView.isShown
}

fun Context?.isActivityLive(): Boolean {
    val activity = this as? Activity ?: return false
    return !activity.isDestroyed
}

fun Activity.getContentView(): View = findViewById(android.R.id.content)