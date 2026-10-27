package com.flowfoundation.wallet.utils

import android.graphics.Bitmap
import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.nft.nftlist.isSvgUrl
import com.flowfoundation.wallet.widgets.SVGWebView
import java.net.URLEncoder


fun ImageView.loadAvatar(url: String, placeholderEnable: Boolean = true, transformation: Transformation<Bitmap>? = null) {
    val avatar = url.parseAvatarUrl()
    logd("loadAvatar", avatar)
    if (avatar.contains("flovatar.com")) {
        loadAvatarNormal(avatar.svgToPng(), placeholderEnable)
    } else {
        loadAvatarNormal(avatar, placeholderEnable, transformation)
    }
}

fun String.svgToPng(): String {
    return "${com.flowfoundation.wallet.network.BASE_HOST}/api/svg2png?url=${URLEncoder.encode(this, "UTF-8")}"
}

fun String.parseBoringAvatar(): String {
    val uriHost = Uri.parse(this).host ?: ""
    if (uriHost.contains("boringavatars")) {
        return this.replace(uriHost, "${android.net.Uri.parse(com.flowfoundation.wallet.network.BASE_HOST).host}/api/avatar")
    }
    return this
}

private fun ImageView.loadAvatarNormal(url: String, placeholderEnable: Boolean = true, transformation: Transformation<Bitmap>? = null) {
    var request = Glide.with(this).load(url)

    if (placeholderEnable) {
        request = request.placeholder(R.drawable.ic_placeholder)
    }
    if (transformation != null) {
        request = request.transform(transformation)
    }
    request.into(this)
}

/**
 * Load image with SVG support
 * If the URL is SVG, creates and returns SVGWebView, otherwise uses normal ImageView loading
 */
fun ImageView.loadImageWithSvgSupport(url: String?, placeholderEnable: Boolean = true): SVGWebView? {
    if (url.isSvgUrl()) {
        // Create SVGWebView to replace ImageView functionality
        val svgWebView = SVGWebView(context)
        svgWebView.loadSvg(url!!)

        // Set up error handling
        svgWebView.onLoadError = { error ->
            logd("SVGWebView", "Failed to load SVG: $error")
            // Fallback to regular image loading
            if (placeholderEnable) {
                setImageResource(R.drawable.ic_placeholder)
            }
        }

        return svgWebView
    } else {
        // Regular image loading
        var request = Glide.with(this).load(url)
        if (placeholderEnable) {
            request = request.placeholder(R.drawable.ic_placeholder)
        }
        request.into(this)
        return null
    }
}
