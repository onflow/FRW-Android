package com.flowfoundation.wallet.page.browser.subpage.filepicker

import android.content.Context
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

private var filePathCallback: ValueCallback<Array<Uri>>? = null
private var fileChooserParams: WebChromeClient.FileChooserParams? = null

fun showWebviewFilePicker(
    context: Context,
    pathCallback: ValueCallback<Array<Uri>>?,
    chooserParams: WebChromeClient.FileChooserParams?
) {
    pathCallback ?: return
    chooserParams ?: return

    filePathCallback = pathCallback
    fileChooserParams = chooserParams

    FilePickerActivity.launch(context)
}

fun webviewFileChooserParams() = fileChooserParams

fun onWebviewFilePicked(uri: Uri?) {
    uri?.let { filePathCallback?.onReceiveValue(arrayOf(uri)) }
}
