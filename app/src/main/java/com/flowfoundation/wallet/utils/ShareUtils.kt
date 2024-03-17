package com.flowfoundation.wallet.utils

import android.content.Context
import android.content.Intent
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.res2String
import java.io.File


/**
 * @param type image/png  application/pdf ...
 */
fun Context.shareFile(file: File, title: String = "Share File", text: String = "", type: String = "*/*") {
    val intent = Intent(Intent.ACTION_SEND)
    if (file.exists()) {
        intent.type = type
        if (text.isNotBlank()) {
            intent.putExtra(Intent.EXTRA_TEXT, text)
        }
        intent.putExtra(Intent.EXTRA_STREAM, file.toContentUri(R.string.file_provider_authorities.res2String()))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivitySafe(Intent.createChooser(intent, title))
    }
}

fun Context.shareText(text: String) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }

    val shareIntent = Intent.createChooser(sendIntent, null)
    startActivitySafe(shareIntent)
}