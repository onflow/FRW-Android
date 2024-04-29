package com.flowfoundation.wallet.network.functions

import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.flowfoundation.wallet.firebase.analytics.reportEvent
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.app.isPreviewnet
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.network.interceptor.HeaderInterceptor
import com.flowfoundation.wallet.utils.*
import com.instabug.library.apm_okhttp_event_listener.InstabugApmOkHttpEventListener
import com.instabug.library.apmokhttplogger.InstabugAPMOkhttpInterceptor
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "FirebaseFunctions"

const val FUNCTION_SIGN_AS_PAYER = "signAsPayer"

// https://us-central1-lilico-dev.cloudfunctions.net/moonPaySignature?url=https://buy-sandbox.moonpay.com?apiKey=pk_test_F0Y1SznEgbvGOWxFYJqStfjLeZ7XT&defaultCurrencyCode=FLOW&colorCode=%23FC814A&walletAddress=0x7d2b880d506db7cc
const val FUNCTION_MOON_PAY_SIGN = "moonPaySignature"

const val FUNCTION_REGISTER = "register"
const val FUNCTION_CREATE_WALLET = "createWallet"

private val HOST = if (isDev()) "https://us-central1-lilico-dev.cloudfunctions.net/" else "https://us-central1-lilico-334404.cloudfunctions.net/"


/**
 * execute firebase function
 */
suspend fun executeFunction(functionName: String, data: Any? = null): String? {
    logd(TAG, "executeFunction:$functionName")
    return execute(functionName, data)
}

/**
 * execute firebase function
 */
suspend fun executeHttpFunction(functionName: String, data: Any? = null): String? {
    logd(TAG, "executeFunction:$functionName")
    return executeHttp(functionName, data)
}

private val functions by lazy { Firebase.functions }

private suspend fun executeHttp(functionName: String, data: Any? = null) = suspendCancellableCoroutine<String?> { continuation ->
    val client = OkHttpClient.Builder().apply {

        callTimeout(10, TimeUnit.SECONDS)
        connectTimeout(10, TimeUnit.SECONDS)
        readTimeout(10, TimeUnit.SECONDS)
        writeTimeout(10, TimeUnit.SECONDS)

        addInterceptor(HeaderInterceptor())
        addInterceptor(InstabugAPMOkhttpInterceptor())
        if (isTesting()) {
            addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        }
        eventListener(InstabugApmOkHttpEventListener())
    }.build()
    val body = if (data == null) data else (if (data is String) data else GsonBuilder().serializeNulls().create().toJson(data))

    val request = Request.Builder().url("$HOST$functionName")
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

private suspend fun execute(functionName: String, data: Any? = null) = suspendCoroutine<String?> { continuation ->
    ioScope {
        val body =
            (if (data == null) data else (if (data is String) data else GsonBuilder().serializeNulls().create().toJson(data))).wrapFunctionBody()
        logd(TAG, "execute $functionName > body:$body")

        functions.getHttpsCallable(functionName)
            .call(body).continueWith { task ->
                if (!task.isSuccessful) {
                    loge(task.exception)
                    reportError(functionName, task.exception?.message, false)
                    continuation.resume(null)
                    return@continueWith
                }

                val result = task.result?.data
                if (result == null) {
                    continuation.resume(null)
                } else {
                    continuation.resume(Gson().toJson(result))
                }
            }
    }
}

private suspend fun String?.wrapFunctionBody(): String {
    return """
        {
        "idToken":"${getFirebaseJwt()}",
        "network":"${getNetwork()}",
        "data": $this
        }
    """.trimIndent()
}

private fun getNetwork(): String {
    return if (isTestnet()) "testnet" else if (isPreviewnet()) "previewnet" else "mainnet"
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