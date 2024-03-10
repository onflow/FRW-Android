package com.flowfoundation.wallet.network.interceptor

import android.os.Build
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.firebase.auth.getFirebaseJwt
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.utils.extensions.capitalizeV2
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.logd
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

private val userAgent by lazy { "${R.string.app_name.res2String()}/${BuildConfig.VERSION_NAME} Build/${BuildConfig.VERSION_CODE} (Android ${Build.VERSION.SDK_INT}; ${deviceName()})" }

private fun deviceName(): String {
    if (Build.MODEL.lowercase().startsWith(Build.MANUFACTURER.lowercase())) {
        return Build.MODEL.capitalizeV2()
    }
    return "${Build.MANUFACTURER.capitalizeV2()} ${Build.MODEL}"
}

class HeaderInterceptor(
    private val ignoreAuthorization: Boolean = false,
    private val network: String? = null,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (ignoreAuthorization) {
            return chain.proceed(chain.request())
        }

        val jwt = runBlocking { getFirebaseJwt() }

        logd("HeaderInterceptor", "jwt:$jwt")
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $jwt")
            .addHeader("User-Agent", userAgent)
            .addHeader("Network", network ?: chainNetWorkString())
            .build()
        return chain.proceed(request)
    }
}