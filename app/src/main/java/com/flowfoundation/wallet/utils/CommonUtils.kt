package com.flowfoundation.wallet.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentActivity
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

fun CharSequence.isLegalAmountNumber(): Boolean {
    val number = toString().toFloatOrNull()
    return number != null && number > 0
}

const val REQUEST_CODE_PICK_PHOTO = 108
fun startGallery(activity: FragmentActivity) {
    val intent = Intent(Intent.ACTION_GET_CONTENT, null)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.type = "image/*"
    intent.putExtra(
        Intent.EXTRA_MIME_TYPES,
        arrayOf("image/png", "image/jpg", "image/jpeg", "application/pdf")
    )

    val chooser = Intent.createChooser(intent, "")
    try {
        activity.startActivityForResult(chooser, REQUEST_CODE_PICK_PHOTO)
    } catch (e: ActivityNotFoundException) {
    }
}