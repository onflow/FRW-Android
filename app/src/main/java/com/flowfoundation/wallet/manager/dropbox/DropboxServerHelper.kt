package com.flowfoundation.wallet.manager.dropbox

import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.ListFolderResult
import com.dropbox.core.v2.files.Metadata
import com.dropbox.core.v2.files.WriteMode
import com.flowfoundation.wallet.utils.loge
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

private val TAG = DropboxServerHelper::class.java.simpleName

class DropboxServerHelper(private val dbxClient: DbxClientV2) {

    private val appFolderPath = "/appDataFolder"
    private val appRootPath = ""

    @Throws(IOException::class)
    fun createFile(fileName: String): String {
        try {
            val path = "/$fileName"
            dbxClient.files().uploadBuilder(path)
                .uploadAndFinish(ByteArrayInputStream("{}".toByteArray()))
            return path
        } catch (e: Exception) {
            loge(TAG, "Error creating file: $e")
            throw IOException("Error creating file", e)
        }
    }

    @Throws(IOException::class)
    fun readFile(filePaths: List<String>): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        for (path in filePaths) {
            try {
                val metadata = dbxClient.files().getMetadata(path) as FileMetadata
                val outputStream = ByteArrayOutputStream()
                dbxClient.files().download(path).download(outputStream)
                results.add(Pair(metadata.name, outputStream.toString("UTF-8")))
            } catch (e: Exception) {
                loge(TAG, "Error reading file at $path: $e")
            }
        }
        if (results.isEmpty()) {
            throw IOException("File not found in any of the possible paths")
        }
        return results
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
    fun getWriteFilePath(fileName: String): String? {
        return try {
            val files = fileList(appRootPath)?.entries
            files?.firstOrNull { it.name == fileName }?.pathLower
        } catch (e: Exception) {
            loge(TAG, "Error getting file ID: $e")
            null
        }
    }

    @Throws(IOException::class)
    fun getReadFilePath(fileName: String): List<String> {
        val possiblePaths = listOf(appRootPath, appFolderPath)
        val results = mutableListOf<String>()

        for (path in possiblePaths) {
            try {
                val files = fileList(path)?.entries
                val file = files?.firstOrNull { it.name == fileName }
                if (file != null) {
                    results.add(file.pathLower!!)
                }
            } catch (e: Exception) {
                loge(TAG, "Error getting file paths in $path: $e")
            }
        }

        return results
    }

    @Throws(IOException::class)
    fun fileList(folderPath: String): ListFolderResult? {
        return try {
            dbxClient.files().listFolder(folderPath)
        } catch (e: Exception) {
            loge(TAG, "Error listing files: $e")
            null
        }
    }

    @Throws(IOException::class)
    fun allFileList(): List<Metadata> {
        val possiblePaths = listOf(appRootPath, appFolderPath)
        val allFiles = mutableListOf<Metadata>()
        for (path in possiblePaths) {
            try {
                val files = fileList(path)?.entries.orEmpty()
                allFiles.addAll(files)
            } catch (e: Exception) {
                loge(TAG, "Error listing files in $path: $e")
            }
        }
        return allFiles.toList()
    }

    @Throws(IOException::class)
    fun writeStringToFile(fileName: String, content: String) {
        try {
            val filePath = getWriteFilePath(fileName) ?: createFile(fileName)
            saveFile(filePath, content)
        } catch (e: Exception) {
            loge(TAG, "Error writing string to file: $e")
            throw IOException("Error writing string to file", e)
        }
    }

}