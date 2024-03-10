package com.flowfoundation.wallet.widgets.floatwindow

import android.app.Activity
import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.flowfoundation.wallet.utils.Env
import java.util.concurrent.ConcurrentHashMap

object FloatWindow {
    private val containerViews = mutableMapOf<String, View>()
    private val configs = ConcurrentHashMap<String, FloatWindowConfig>()

    fun builder(): FloatWindowBuilder = FloatWindowBuilder()

    fun dismiss(tag: String) {
        (containerViews[tag]?.parent as? ViewGroup)?.removeView(containerViews[tag])
        removeConfig(tag)
    }

    fun isShowing(tag: String): Boolean {
        return containerViews[tag] != null && containerViews[tag]?.parent != null
    }

    internal fun show(config: FloatWindowConfig, activity: Activity) {
        if (isShowing(config.tag)) {
            return
        }

        configs[config.tag] = config

        val contentView = config.contentView ?: LayoutInflater.from(Env.getApp()).inflate(config.layoutId, null)
        containerViews[config.tag] = contentView
        activity.rootView()?.addView(contentView, createParams(config))

        FloatWindowPageObserver.register(Env.getApp() as Application)
    }

    internal fun onPageChange(activity: Activity) {
        configs.forEach { (tag, config) ->
            if (isShowing(tag) && !config.ignorePage.contains(activity::class)) {
                dismiss(tag)
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