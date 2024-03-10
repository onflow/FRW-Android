package com.flowfoundation.wallet.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

fun textToClipboard(text: String) {
    val clipboard = Env.getApp().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip: ClipData = ClipData.newPlainText("", text)
    clipboard.setPrimaryClip(clip)
}

fun clipboardToText(): CharSequence? {
    val clipboard = Env.getApp().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.text
}