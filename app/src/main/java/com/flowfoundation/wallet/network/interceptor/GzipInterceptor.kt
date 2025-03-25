package com.flowfoundation.wallet.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.zip.GZIPInputStream

class GzipRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val compressedRequest = originalRequest.newBuilder()
            .header("Accept-Encoding", "gzip")
            .build()
        return chain.proceed(compressedRequest)
    }
}
class GzipResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body
        if (body != null && isGzipped(response)) {
            val gzippedInputStream = GZIPInputStream(body.byteStream())
            val unzippedBytes = gzippedInputStream.readBytes()
            val unzippedBody = unzippedBytes.toResponseBody(body.contentType())
            return response.newBuilder().body(unzippedBody).build()
        }
        return response
    }

    private fun isGzipped(response: Response): Boolean {
        val contentEncoding = response.header("Content-Encoding")
        return contentEncoding != null && contentEncoding.contains("gzip", ignoreCase = true)
    }
}
