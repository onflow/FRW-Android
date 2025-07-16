package com.flowfoundation.wallet.utils

import android.annotation.SuppressLint
import android.content.Context
import com.flow.wallet.storage.StorageProtocol
import com.flow.wallet.storage.FileSystemStorage
import java.io.File

@SuppressLint("StaticFieldLeak")
object Env {
    private lateinit var originContext: Context

    private lateinit var context: Context

    private var storageInstance: StorageProtocol? = null

    fun init(ctx: Context) {
        originContext = ctx
        context = originContext
    }

    @JvmStatic
    fun getApp(): Context {
        return context
    }

    fun getStorage(): StorageProtocol {
        return storageInstance ?: FileSystemStorage(File(getApp().filesDir, "wallet")).also {
            storageInstance = it
        }
    }
}
