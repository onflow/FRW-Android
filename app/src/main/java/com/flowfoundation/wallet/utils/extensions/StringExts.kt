package com.flowfoundation.wallet.utils.extensions

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_DARK
import androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_SYSTEM
import androidx.core.content.ContextCompat.startActivity
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.drawableResToBitmap
import com.flowfoundation.wallet.utils.isNightMode
import java.net.URL
import java.net.URLEncoder


fun String.indexOfAll(s: String): List<Int> {
    val result = mutableListOf<Int>()
    var index: Int = indexOf(s)
    while (index >= 0) {
        println(index)
        result.add(index)
        index = indexOf(s, index + 1)
    }
    return result
}

fun String.capitalizeV2(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

fun String.removeUrlParams(): String {
    return this.split("?")[0]
}

fun String.urlEncode(): String = URLEncoder.encode(this)

fun String.openInSystemBrowser(context: Context, ignoreInAppBrowser: Boolean = false) {
    if (ignoreInAppBrowser) {
        startActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(this)), null)
    } else {
        val closeIcon = drawableResToBitmap(context, R.drawable.ic_baseline_close_24, R.color.gray_55.res2color())
        val customTabsIntent = CustomTabsIntent.Builder().apply {
            if (closeIcon != null) setCloseButtonIcon(closeIcon)
            setColorScheme(if (isNightMode()) COLOR_SCHEME_DARK else COLOR_SCHEME_SYSTEM)
        }.build()
        customTabsIntent.launchUrl(context, Uri.parse(this))
    }
}

fun String.urlHost(): String {
    return try {
        URL(this).host
    } catch (e: Exception) {
        return this
    }
}

// convert string to md5 string
fun String.md5(): String {
    val md = java.security.MessageDigest.getInstance("MD5")
    val digested = md.digest(toByteArray())
    return digested.joinToString("") {
        String.format("%02x", it)
    }
}