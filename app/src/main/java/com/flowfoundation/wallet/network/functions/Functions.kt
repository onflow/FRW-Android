package com.flowfoundation.wallet.network.functions

import com.google.gson.GsonBuilder
import com.flowfoundation.wallet.firebase.analytics.reportEvent
import com.flowfoundation.wallet.network.interceptor.HeaderInterceptor
import com.flowfoundation.wallet.network.interceptor.PayerServiceInterceptor
import com.flowfoundation.wallet.utils.*
import ai.luciq.library.okhttp.v2.LuciqOkHttpInterceptor
import ai.luciq.library.okhttp.v2.LuciqOkHttpEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "FirebaseFunctions"

const val FUNCTION_SIGN_AS_PAYER = "/api/signAsFeePayer"  // Need leading slash for BASE_HOST
const val FUNCTION_SIGN_AS_BRIDGE_PAYER = "/api/signAsBridgePayer"

// https://us-central1-lilico-dev.cloudfunctions.net/moonPaySignature?url=https://buy-sandbox.moonpay.com?apiKey=pk_test_F0Y1SznEgbvGOWxFYJqStfjLeZ7XT&defaultCurrencyCode=FLOW&colorCode=%23FC814A&walletAddress=0x7d2b880d506db7cc
const val FUNCTION_MOON_PAY_SIGN = "moonPaySignature"

private val HOST = if (isDev()) "https://us-central1-lilico-dev.cloudfunctions.net/" else "https://us-central1-lilico-334404.cloudfunctions.net/"


/**
 * execute firebase function
 */
suspend fun executeHttpFunction(functionName: String, data: Any? = null, host: String? = HOST): String? {
    logd(TAG, "executeFunction:$functionName")
    return executeHttp(host ?: HOST, functionName, data)
}


private suspend fun executeHttp(host: String, functionName: String, data: Any? = null) = suspendCancellableCoroutine { continuation ->
    val client = OkHttpClient.Builder().apply {

        callTimeout(10, TimeUnit.SECONDS)
        connectTimeout(10, TimeUnit.SECONDS)
        readTimeout(10, TimeUnit.SECONDS)
        writeTimeout(10, TimeUnit.SECONDS)

        addInterceptor(HeaderInterceptor())
        addInterceptor(PayerServiceInterceptor())
        addInterceptor(LuciqOkHttpInterceptor())
        eventListenerFactory(LuciqOkHttpEventListener.Factory())
        if (isTesting()) {
            addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        }
    }.build()
    val body = if (data == null) data else (data as? String ?: GsonBuilder().serializeNulls().create().toJson(data))

    // Fix double slash issue by removing trailing slash from host or leading slash from functionName
    val url = when {
        host.endsWith("/") && functionName.startsWith("/") -> host + functionName.substring(1)
        host.endsWith("/") || functionName.startsWith("/") -> host + functionName
        else -> "$host/$functionName"
    }

    val request = Request.Builder().url(url)
        .post(body.orEmpty().toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()
    val response = client.newCall(request).execute()

    if (!response.isSuccessful) {
        logw(TAG, response.toString())
        continuation.resume(null)
        reportError(functionName, response.message, true)
        return@suspendCancellableCoroutine
    }
    continuation.resume(response.body?.string())
}

private fun reportError(functionName: String, message: String?, isHttp: Boolean) {
    reportEvent(
        "firebase_function_error", mapOf(
            "function" to functionName,
            "isHttp" to "$isHttp",
            "message" to message.orEmpty(),
        )
    )
}
