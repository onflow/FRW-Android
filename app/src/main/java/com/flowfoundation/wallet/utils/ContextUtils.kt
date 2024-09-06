package com.flowfoundation.wallet.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.Service
import android.content.*
import android.os.Build
import android.os.IBinder
import android.view.View

/**
 * @author wangkai
 */


fun broadcastReceiver(callback: (Context, Intent) -> Unit): BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = callback(context, intent)
}

fun findActivity(view: View?): Activity? {
    return if (view == null) {
        null
    } else getActivityFromContext(view.context)
}

fun isActivityAlive(view: View?): Boolean {
    if (view == null) return false
    val activity = getActivityFromContext(view.context)
    return isActivityAlive(activity)
}

fun isActivityAlive(context: Context?): Boolean {
    if (context == null) return false
    val activity = getActivityFromContext(context)
    return isActivityAlive(activity)
}

fun isActivityAlive(activity: Activity?): Boolean {
    return !(activity == null || activity.isFinishing || activity.isDestroyed)
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

fun isServiceActive(clz: Class<out Service>): Boolean {
    val manager = Env.getApp().getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    manager.getRunningServices(Integer.MAX_VALUE)?.forEach {
        if (clz.name == it.service.className) {
            return true
        }
    }
    return false
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