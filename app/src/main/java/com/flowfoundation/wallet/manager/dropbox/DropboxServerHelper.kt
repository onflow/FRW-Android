package com.flowfoundation.wallet.manager.dropbox

import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.ListFolderResult
import com.dropbox.core.v2.files.WriteMode
import com.flowfoundation.wallet.utils.loge
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

private val TAG = DropboxServerHelper::class.java.simpleName

class DropboxServerHelper(private val dbxClient: DbxClientV2) {

    private val appFolderPath = "/appDataFolder"

    @Throws(IOException::class)
    fun createFile(fileName: String): String {
        try {
            val path = "$appFolderPath/$fileName"
            dbxClient.files().uploadBuilder(path)
                .uploadAndFinish(ByteArrayInputStream("{}".toByteArray()))
            return path
        } catch (e: Exception) {
            loge(TAG, "Error creating file: $e")
            throw IOException("Error creating file", e)
        }
    }

    @Throws(IOException::class)
    fun readFile(filePath: String): Pair<String, String> {
        try {
            val metadata = dbxClient.files().getMetadata(filePath) as FileMetadata
            val outputStream = ByteArrayOutputStream()
            dbxClient.files().download(filePath).download(outputStream)
            return Pair(metadata.name, outputStream.toString("UTF-8"))
        } catch (e: Exception) {
            loge(TAG, "Error reading file: $e")
            throw IOException("Error reading file", e)
        }
    }

    @Throws(IOException::class)
    fun saveFile(filePath: String, content: String) {
        try {
            dbxClient.files().uploadBuilder(filePath)
                .withMode(WriteMode.OVERWRITE)
                .uploadAndFinish(ByteArrayInputStream(content.toByteArray()))
        } catch (e: Exception) {
            loge(TAG, "Error saving file: $e")
            throw IOException("Error saving file", e)
        }
    }

    @Throws(IOException::class)
    fun getFilePath(fileName: String): String? {
        return try {
            val files = fileList()?.entries
            files?.firstOrNull { it.name == fileName }?.pathLower
        } catch (e: Exception) {
            loge(TAG, "Error getting file ID: $e")
            null
        }
    }

    @Throws(IOException::class)
    fun fileList(): ListFolderResult? {
        return try {
            dbxClient.files().listFolder(appFolderPath)
        } catch (e: Exception) {
            loge(TAG, "Error listing files: $e")
            null
        }
    }

    @Throws(IOException::class)
    fun writeStringToFile(fileName: String, content: String) {
        try {
            val filePath = getFilePath(fileName) ?: createFile(fileName)
            saveFile(filePath, content)
        } catch (e: Exception) {
            loge(TAG, "Error writing string to file: $e")
            throw IOException("Error writing string to file", e)
        }
    }

    @Throws(IOException::class)
    fun deleteFile(filePath: String) {
        try {
            dbxClient.files().deleteV2(filePath)
        } catch (e: Exception) {
            loge(TAG, "Error deleting file: $e")
            throw IOException("Error deleting file", e)
        }
    }
}