package com.flowfoundation.wallet.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration

@SuppressLint("StaticFieldLeak")
object Env {
    private lateinit var originContext: Context

    private lateinit var context: Context

    fun init(ctx: Context) {
        originContext = ctx
        context = originContext
    }

    @JvmStatic
    fun getApp(): Context {
        return context
    }

    fun updateContextConfig(config: Configuration) {
        context = originContext.createConfigurationContext(config)
    }
}
