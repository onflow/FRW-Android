package com.flowfoundation.wallet.utils.extensions

import android.graphics.drawable.Drawable
import android.net.Uri
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.logd


fun Uri?.getDrawable(): Drawable? {
    this ?: return null
    logd("UriExts", "getDrawable():$this")
//    Env.getApp().contentResolver.openInputStream(this).use {
//        logd("UriExts", "stream:$it")
//        return Drawable.createFromStream(it, null)
//    }
    return Drawable.createFromStream(Env.getApp().contentResolver.openInputStream(this), null)
}