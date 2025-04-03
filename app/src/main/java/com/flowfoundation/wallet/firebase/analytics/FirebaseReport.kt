package com.flowfoundation.wallet.firebase.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.debug.fragments.debugViewer.DebugViewerDataSource
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.nftco.flow.sdk.FlowException
import retrofit2.HttpException
import java.util.*

fun reportEvent(event: String, params: Map<String, String> = mapOf()) {
    ioScope {
        val bundle = Bundle()
        params.forEach { bundle.putString(it.key, it.value) }
        bundle.putString("country", Locale.getDefault().country)
        bundle.putString("language", Locale.getDefault().language)
        bundle.putString("OS", "Android")
        bundle.putString("brand", android.os.Build.BRAND)
        bundle.putString("system", android.os.Build.VERSION.RELEASE)
        FirebaseAnalytics.getInstance(Env.getApp()).logEvent(event, bundle)
        if (BuildConfig.DEBUG) {
            logd("report", "$event,params:$params")
        }
    }
}

fun reportException(event: String, ex: Throwable?, params: Map<String, String>? = null) {
    reportEvent(
        event, mutableMapOf(
            "exception" to ex?.javaClass?.simpleName.orEmpty(),
            "message" to ex?.message.orEmpty(),
        ).apply {
            params?.forEach { put(it.key, it.value) }
        }
    )
    reportErrorToDebugView(ex?.javaClass?.simpleName.orEmpty(), mutableMapOf(
        "message" to ex?.message.orEmpty(),
    ).apply {
        params?.forEach { put(it.key, it.value) }
        when (ex) {
            is HttpException -> put("response", ex.response().toString())
            is FlowException -> put("cause", ex.cause.toString())
        }
    })
}


fun reportErrorToDebugView(event: String?, params: Map<String, String> = mapOf()) {
    DebugViewerDataSource.error(event ?: "Error", params.toString())
}