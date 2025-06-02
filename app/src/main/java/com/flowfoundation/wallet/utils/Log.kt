package com.flowfoundation.wallet.utils

import android.util.Log
import com.flow.wallet.errors.WalletError
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.firebase.analytics.reportErrorToDebugView
import com.flowfoundation.wallet.firebase.analytics.reportException
import com.flowfoundation.wallet.utils.debug.fragments.debugViewer.DebugViewerDataSource
import com.instabug.library.logging.InstabugLog
import com.instabug.library.Instabug
import retrofit2.HttpException

private const val MAX_LOG_LENGTH = 3500
private const val LOG_PREFIX = "["
private const val LOG_SUFFIX = "]"

fun logv(tag: String?, msg: Any?) = logWithLevel(tag, msg, Log.VERBOSE, InstabugLog::v)
fun logd(tag: String?, msg: Any?) = logWithLevel(tag, msg, Log.DEBUG, InstabugLog::d)
fun logi(tag: String?, msg: Any?) = logWithLevel(tag, msg, Log.INFO, InstabugLog::i)
fun logw(tag: String?, msg: Any?) = logWithLevel(tag, msg, Log.WARN, InstabugLog::w)
fun loge(tag: String?, msg: Any?) {
    logWithLevel(tag, msg, Log.ERROR, InstabugLog::e)
    reportErrorToDebugView(tag, mapOf("errorInfo" to (msg?.toString() ?: "")))
}

fun loge(throwable: Throwable?, printStackTrace: Boolean = true, report: Boolean = true) {
    val message = throwable?.message ?: ""
    log("Exception", message, Log.ERROR)
    
    if (isInstabugInitialized()) {
        InstabugLog.e("Exception: $message")
    }
    
    if (printLog() && printStackTrace) {
        throwable?.printStackTrace()
    }
    if (report) {
        ioScope { throwable?.let { reportException("exception_report", it) } }
    }
}

fun reportApiErrorToDebugView(api: String, throwable: Throwable?) {
    val title = throwable?.javaClass?.simpleName ?: "api_error"
    val params = mapOf(
        "api" to api,
        "message" to throwable?.message.orEmpty(),
        "response" to (throwable as? HttpException)?.response().toString()
    )
    DebugViewerDataSource.error(title, params.toString())
}

fun reportCadenceErrorToDebugView(cadence: String, throwable: Throwable?) {
    val title = throwable?.javaClass?.simpleName ?: "cadence_error"
    val params = mapOf(
        "cadence" to cadence,
        "message" to throwable?.message.orEmpty(),
        "cause" to (throwable as? WalletError)?.cause.toString()
    )
    DebugViewerDataSource.error(title, params.toString())
}

private fun logWithLevel(tag: String?, msg: Any?, level: Int, instabugLog: (String) -> Unit) {
    log(tag, msg, level)
    
    if (isInstabugInitialized()) {
        try {
            instabugLog("${tag.orEmpty()}: ${msg?.toString().orEmpty()}")
        } catch (e: Exception) {
            // Silently fail if there's still an issue with Instabug
            // This prevents logging errors from causing crashes or more error logs
        }
    }
}

private fun isInstabugInitialized(): Boolean {
    return try {
        // Try to check if Instabug is initialized
        // This will return true if Instabug is properly initialized
        Instabug.isBuilt()
    } catch (e: Exception) {
        // If any exception occurs, assume Instabug is not initialized
        false
    }
}

private fun log(tag: String?, msg: Any?, level: Int) {
    if (!printLog()) return

    val formattedTag = "$LOG_PREFIX${tag.orEmpty()}$LOG_SUFFIX"
    val text = msg?.toString() ?: return

    if (text.length > MAX_LOG_LENGTH) {
        text.chunked(MAX_LOG_LENGTH).forEach { chunk ->
            print(formattedTag, chunk, level)
        }
    } else {
        print(formattedTag, text, level)
    }
}

private fun print(tag: String, msg: String, level: Int) {
    when (level) {
        Log.VERBOSE -> Log.v(tag, msg)
        Log.DEBUG -> Log.d(tag, msg)
        Log.INFO -> Log.i(tag, msg)
        Log.WARN -> Log.w(tag, msg)
        Log.ERROR -> Log.e(tag, msg)
    }
}

private fun printLog() = BuildConfig.DEBUG || isDev()