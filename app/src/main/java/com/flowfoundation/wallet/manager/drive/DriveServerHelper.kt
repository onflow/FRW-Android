package com.flowfoundation.wallet.manager.drive

import android.util.Pair
import androidx.annotation.WorkerThread
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.flowfoundation.wallet.utils.logd
import java.io.IOException
import java.util.*


class DriveServerHelper(private val driveService: Drive) {

    /**
     * Creates a text file in the user's My Drive folder and returns its file ID.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun createFile(fileName: String): String {
        val metadata = File()
            .setParents(Collections.singletonList("appDataFolder"))
            .setMimeType("application/json")
            .setName(fileName)
        val googleFile: File = driveService.files().create(metadata).execute()
            ?: throw IOException("Null result when requesting file creation.")
        return googleFile.id
    }

    /**
     * Opens the file identified by {@code fileId} and returns a {@link Pair} of its name and
     * contents.
     */
    @Throws(IOException::class)
    @WorkerThread
    fun readFile(fileId: String): Pair<String, String> {
        // Retrieve the metadata as a File object.

        // Retrieve the metadata as a File object.
        val metadata: File = driveService.files().get(fileId).execute()

        // Stream the file contents to a String.
        val content = driveService.files().get(fileId).executeMediaAsInputStream().bufferedReader().readText()
        return Pair(metadata.name, content)
    }

    /**
     * Updates the file identified by {@code fileId} with the given {@code name} and {@code
     * content}.
     */
    @Throws(IOException::class)// Create a File containing any metadata changes.
    @WorkerThread
    fun saveFile(fileId: String, name: String, content: String) {
        // Create a File containing any metadata changes.
        val metadata = File().setName(name)

        // Convert content to an AbstractInputStreamContent instance.
        val contentStream = ByteArrayContent.fromString("application/json", content)

        // Update the metadata and contents.
        driveService.files().update(fileId, metadata, contentStream).execute()
    }

    @Throws(IOException::class)
    @WorkerThread
    fun getFileId(fileName: String): String? {
        val files = driveService.files().list()
            .setSpaces("appDataFolder")
            .setFields("nextPageToken, files(id, name)")
            .setPageSize(10)
            .execute()
            .files.filterNotNull()
        for (file in files) {
            if (file.name == fileName) {
                return file.id
            }
        }
        return null
    }

    @Throws(IOException::class)
    @WorkerThread
    fun fileList(): FileList? {
        return driveService.files().list()
            .setSpaces("appDataFolder")
            .setFields("nextPageToken, files(id, name)")
            .setPageSize(10)
            .execute()
    }

    @Throws(IOException::class)
    @WorkerThread
    fun writeStringToFile(fileName: String, content: String) {
        var fileId = getFileId(fileName)
        if (fileId == null) {
            logd("DriveServerHelper", "writeStringToFile fileId is null")
            val googleFile: File = driveService.files().create(metadata(fileName)).execute()
                ?: throw IOException("Null result when requesting file creation.")
            fileId = googleFile.id
            logd("DriveServerHelper", "writeStringToFile create fileId：$fileId")
        }
        saveFile(fileId!!, fileName, content)
    }

    private fun metadata(fileName: String) = File()
        .setParents(Collections.singletonList("appDataFolder"))
        .setMimeType("application/json")
        .setName(fileName)
}