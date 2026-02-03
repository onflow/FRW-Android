package com.flowfoundation.wallet.widgets.floatwindow

import android.app.Activity
import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.flowfoundation.wallet.utils.Env
import java.util.concurrent.ConcurrentHashMap

import com.flowfoundation.wallet.utils.logd

object FloatWindow {
    private val containerViews = mutableMapOf<String, View>()
    private val configs = ConcurrentHashMap<String, FloatWindowConfig>()

    fun builder(): FloatWindowBuilder = FloatWindowBuilder()

    fun dismiss(tag: String) {
        detach(tag)
        removeConfig(tag)
    }

    private fun detach(tag: String) {
        if (containerViews[tag]?.parent != null) {
            (containerViews[tag]?.parent as? ViewGroup)?.removeView(containerViews[tag])
        }
    }

    fun isShowing(tag: String): Boolean {
        return containerViews[tag] != null && containerViews[tag]?.parent != null
    }

    internal fun show(config: FloatWindowConfig, activity: Activity) {
        if (isShowing(config.tag)) {
            // Check if attached to the same activity
            if (containerViews[config.tag]?.parent == activity.rootView()) {
                containerViews[config.tag]?.bringToFront()
                return
            }
            // If attached to a different activity, detach first
            detach(config.tag)
        }

        configs[config.tag] = config

        val contentView = config.contentView ?: LayoutInflater.from(Env.getApp()).inflate(config.layoutId, null)
        containerViews[config.tag] = contentView

        val decorView = activity.rootView()
        decorView?.post {
            if (contentView.parent != null && contentView.parent != decorView) {
                (contentView.parent as? ViewGroup)?.removeView(contentView)
            }

            if (contentView.parent == null) {
                decorView.addView(contentView, createParams(config))
            }

            contentView.elevation = 9999f
            contentView.bringToFront()
        }

        FloatWindowPageObserver.register(Env.getApp() as Application)
    }

    internal fun onPageChange(activity: Activity) {
        configs.forEach { (tag, config) ->
            if (config.ignorePage.contains(activity::class)) {
                if (isShowing(tag)) {
                    detach(tag)
                }
            } else {
                // Always try to show if config exists and page is not ignored
                // The show() method handles the check if it's already showing on current activity
                show(config, activity)
            }
        }
    }

    private fun Activity.rootView() = this.window?.decorView as? ViewGroup

    private fun removeConfig(tag: String) {
        configs.remove(tag)
        containerViews.remove(tag)
    }
}

class FloatWindowBuilder {
    var config = FloatWindowConfig()

    fun setConfig(config: FloatWindowConfig) = apply { this.config = config }

    fun show(activity: Activity) {
        FloatWindow.show(config, activity)
    }
}
