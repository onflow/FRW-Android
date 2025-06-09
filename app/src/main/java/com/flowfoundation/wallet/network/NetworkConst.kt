package com.flowfoundation.wallet.network

import com.flowfoundation.wallet.network.interceptor.GzipRequestInterceptor
import com.flowfoundation.wallet.network.interceptor.GzipResponseInterceptor
import com.flowfoundation.wallet.network.interceptor.HeaderInterceptor
import com.flowfoundation.wallet.utils.isDev
import com.flowfoundation.wallet.utils.isTesting
import com.instabug.library.okhttplogger.InstabugOkhttpInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit


val API_HOST = if (isDev()) "https://dev.lilico.app" else "https://api.lilico.app"
val EVM_API_HOST = if (isDev()) "https://test.lilico.app" else "https://api.lilico.app"
val BASE_HOST = if (isDev()) "https://web-dev.api.wallet.flow.com" else "https://web.api.wallet.flow.com"

fun retrofit(
    disableConverter: Boolean = false,
    network: String? = null,
): Retrofit {
    val client = OkHttpClient.Builder().apply {
        addInterceptor(HeaderInterceptor(network = network))
        addInterceptor(InstabugOkhttpInterceptor())

        callTimeout(20, TimeUnit.SECONDS)
        connectTimeout(20, TimeUnit.SECONDS)
        readTimeout(20, TimeUnit.SECONDS)
        writeTimeout(20, TimeUnit.SECONDS)

        if (isTesting() || isDev()) {
            addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        }
    }.build()

    val builder = Retrofit.Builder()
    if (disableConverter) {
        builder.addConverterFactory(ScalarsConverterFactory.create())
    } else {
        builder.addConverterFactory(GsonConverterFactory.create())
    }
    return builder.baseUrl(API_HOST).client(client).build()
}

fun retrofitApi(): Retrofit {
    return retrofitWithHost(BASE_HOST, ignoreAuthorization = false)
}

fun cadenceScriptApi(): Retrofit {
    val client = OkHttpClient.Builder().apply {
        addInterceptor(HeaderInterceptor(false))
        addInterceptor(InstabugOkhttpInterceptor())
        addInterceptor(GzipRequestInterceptor())
        addInterceptor(GzipResponseInterceptor())
        callTimeout(20, TimeUnit.SECONDS)
        connectTimeout(20, TimeUnit.SECONDS)
        readTimeout(20, TimeUnit.SECONDS)
        writeTimeout(20, TimeUnit.SECONDS)

        if (isTesting() || isDev()) {
            addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        }
    }.build()

    val builder = Retrofit.Builder()
    builder.addConverterFactory(GsonConverterFactory.create())
    return builder.baseUrl(BASE_HOST).client(client).build()
}

fun retrofitWithHost(host: String, disableConverter: Boolean = false, ignoreAuthorization: Boolean = true): Retrofit {
    val client = OkHttpClient.Builder().apply {
        addInterceptor(HeaderInterceptor(ignoreAuthorization))
        addInterceptor(InstabugOkhttpInterceptor())
        callTimeout(20, TimeUnit.SECONDS)
        connectTimeout(20, TimeUnit.SECONDS)
        readTimeout(20, TimeUnit.SECONDS)
        writeTimeout(20, TimeUnit.SECONDS)

        if (isTesting() || isDev()) {
            addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        }
    }.build()

    val builder = Retrofit.Builder()
    if (disableConverter) {
        builder.addConverterFactory(ScalarsConverterFactory.create())
    } else {
        builder.addConverterFactory(GsonConverterFactory.create())
    }
    return builder.baseUrl(host).client(client).build()
}

fun retrofitEVM(
    disableConverter: Boolean = false,
    network: String? = null,
): Retrofit {
    val client = OkHttpClient.Builder().apply {
        addInterceptor(HeaderInterceptor(network = network))
        addInterceptor(InstabugOkhttpInterceptor())

        callTimeout(20, TimeUnit.SECONDS)
        connectTimeout(20, TimeUnit.SECONDS)
        readTimeout(20, TimeUnit.SECONDS)
        writeTimeout(20, TimeUnit.SECONDS)

        if (isTesting() || isDev()) {
            addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        }
    }.build()

    val builder = Retrofit.Builder()
    if (disableConverter) {
        builder.addConverterFactory(ScalarsConverterFactory.create())
    } else {
        builder.addConverterFactory(GsonConverterFactory.create())
    }
    return builder.baseUrl(EVM_API_HOST).client(client).build()
}