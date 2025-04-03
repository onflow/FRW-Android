package com.flowfoundation.wallet.utils

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.View

/**
 * @author wangkai
 */

fun findActivity(view: View?): Activity? {
    return if (view == null) {
        null
    } else getActivityFromContext(view.context)
}

fun getActivityFromContext(context: Context): Activity? {
    var ctx = context
    if (ctx is Activity) {
        return ctx
    }
    if (ctx is ContextWrapper) {
        ctx = ctx.baseContext
    }
    return if (ctx is Activity) {
        ctx
    } else null
}

fun Context.startActivitySafe(intent: Intent) {
    if (this is Activity) {
        startActivity(intent)
    } else {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}

fun Context.startServiceSafe(intent: Intent) {

    bindService(intent, object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
    }, Context.BIND_AUTO_CREATE)
}