package com.flowfoundation.wallet.utils

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Files

val CACHE_PATH: File = Env.getApp().cacheDir.apply { if (!exists()) mkdirs() }

val DATA_PATH: File = File(Env.getApp().dataDir, "data").apply { if (!exists()) mkdirs() }

val CACHE_VIDEO_PATH: File = File(CACHE_PATH, "video").apply { if (!exists()) mkdirs() }

fun File.toContentUri(authority: String): Uri {
    return FileProvider.getUriForFile(Env.getApp(), authority, this)
}

@WorkerThread
fun Uri?.toFile(path: String): File? {
    if (this == null) return null
    val file = File(path)
    return try {
        Env.getApp().contentResolver.openInputStream(this)?.use { it.toFile(file) }
        file
    } catch (e: Exception) {
        loge(e)
        return null
    }

}

// delete all file in cache folder
fun clearCacheDir() {
    CACHE_PATH.listFiles()?.forEach { it.delete() }
}

fun InputStream.toFile(file: File) {
    this.use {
        file.outputStream().use { outputStream ->
            val byte = ByteArray(2048)
            while (true) {
                val length = this.read(byte)
                if (length <= 0) {
                    break
                }
                outputStream.write(byte, 0, length)
            }
            outputStream.flush()
        }
    }
}

fun Bitmap.saveToFile(file: File, fileType: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): File {
    file.parentFile?.let { if (!it.exists()) it.mkdirs() }
    file.outputStream().use {
        compress(fileType, 95, it)
        it.flush()
    }
    return file
}

fun Bitmap.saveToCache(fileName: String, fileType: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): File {
    val file = File(CACHE_PATH, fileName)
    return saveToFile(file, fileType)
}

@WorkerThread
fun String?.saveToFile(file: File) {
    if (this.isNullOrBlank()) {
        return
    }
    if (file.parentFile?.exists() != true) {
        file.parentFile?.mkdirs()
    }
    file.printWriter().use { it.write(this) }
}

@WorkerThread
fun File?.read(): String {
    if (this?.exists() != true) {
        return ""
    }
    BufferedReader(InputStreamReader(inputStream())).use { reader ->
        return reader.readText()
    }
}

fun readTextFromAssets(path: String): String? {
    try {
        BufferedReader(InputStreamReader(Env.getApp().assets.open(path))).use { reader ->
            return reader.readText()
        }
    } catch (e: Exception) {
        return null
    }
}

/**
 * download file from net, and save to gallery
 */
fun String.downloadToGallery(toast: String = "") {
    ioScope {
        safeRun {
            val fileName = "${System.currentTimeMillis()}${urlFileName()}"
            val file = File(CACHE_PATH, fileName)
            URL(this).openStream().use { Files.copy(it, file.toPath()) }
            with(ContentValues()) {
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.DATA, file.absolutePath)
                Env.getApp().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, this)
            }

            if (toast.isNotBlank()) {
                toast(msg = toast)
            }
        }
    }
}

private fun String.urlFileName() = this.split("/").last()