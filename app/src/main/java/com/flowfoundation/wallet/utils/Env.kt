package com.flowfoundation.wallet.utils

import android.annotation.SuppressLint
import android.content.Context

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
}
