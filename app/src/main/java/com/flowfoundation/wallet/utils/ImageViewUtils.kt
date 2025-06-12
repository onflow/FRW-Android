package com.flowfoundation.wallet.utils

import android.graphics.Bitmap
import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.nft.nftlist.getBase64SvgModel
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLEncoder
import androidx.core.net.toUri


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
    return "https://lilico.app/api/svg2png?url=${URLEncoder.encode(this, "UTF-8")}"
}

fun String.parseBoringAvatar(): String {
    val originalUrl = runCatching { this.toHttpUrl() }.getOrNull() ?: return this

    if (!originalUrl.host.contains("boringavatars")) return this

    val newUrlBuilder = originalUrl.newBuilder()
        .host("lilico.app")
        .encodedPath("/api/avatar${originalUrl.encodedPath}")
        .removeAllQueryParameters("square")
        .addQueryParameter("square", "true")

    return newUrlBuilder.build().toString()
}

private fun ImageView.loadAvatarNormal(url: String, placeholderEnable: Boolean = true, transformation: Transformation<Bitmap>? = null) {
    var request = Glide.with(this).load(url.getBase64SvgModel() ?: url)

    if (placeholderEnable) {
        request = request.placeholder(R.drawable.ic_placeholder)
    }
    if (transformation != null) {
        request = request.transform(transformation)
    }
    request.into(this)
}
