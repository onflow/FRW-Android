package com.flowfoundation.wallet.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.flowfoundation.wallet.BuildConfig


fun safeRun(printLog: Boolean = true, block: () -> Unit) {
    return try {
        block()
    } catch (e: Throwable) {
        if (printLog && BuildConfig.DEBUG) {
            loge(e)
        } else {
        }
    }
}

fun sendEmail(
    context: Context,
    email: String,
    subject: String = "",
    message: String = "",
    chooserTitle: String = "Send Email",
) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:$email")
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, message)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivitySafe(intent)
    } catch (e: Throwable) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, message)
        intent.type = "message/rfc822"
        context.startActivitySafe(Intent.createChooser(intent, chooserTitle))
    }
}
