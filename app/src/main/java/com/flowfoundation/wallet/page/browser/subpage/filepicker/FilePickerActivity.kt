package com.flowfoundation.wallet.page.browser.subpage.filepicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.CallSuper
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.utils.extensions.res2String

class FilePickerActivity : BaseActivity() {

    private val picker by lazy {
        registerForActivityResult(FilePickerResultContract(webviewFileChooserParams()!!)) { uri: Uri? ->
            onWebviewFilePicked(uri)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val param = webviewFileChooserParams()

        if (param == null) {
            onWebviewFilePicked(null)
            finish()
            return
        }

        picker.launch(param.acceptType())
    }

    private fun WebChromeClient.FileChooserParams.acceptType(): String {
        return acceptTypes.getOrElse(0) { "*/*" }.split("/").first() + "/*"
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, FilePickerActivity::class.java))
        }
    }
}

private class FilePickerResultContract(private val params: WebChromeClient.FileChooserParams) : ActivityResultContract<String, Uri?>() {
    @CallSuper
    override fun createIntent(context: Context, input: String): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input)
        with(intent) {
            if (params.acceptTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, params.acceptTypes)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }

        return Intent.createChooser(intent, R.string.file_picker_title.res2String())
    }

    override fun getSynchronousResult(
        context: Context,
        input: String
    ): SynchronousResult<Uri?>? {
        return null
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (intent == null || resultCode != Activity.RESULT_OK) null else intent.data
    }
}