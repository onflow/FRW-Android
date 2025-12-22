package com.flowfoundation.wallet.pdfparser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages document picker for PDF file selection and processing
 */
class DocumentPickerManager(private val activity: Activity) {

    companion object {
        private const val TAG = "PDF_IMPORT"
        const val PICK_PDF_REQUEST = 1001
    }

    /**
     * Callback interface for PDF selection results
     */
    interface PDFSelectionCallback {
        fun onSuccess(jsonData: String, fileName: String)
        fun onError(error: String)
        fun onCancelled()
        /**
         * Called when PDF is password-protected and requires password
         * @param fileName Name of the PDF file
         * @param pdfUri URI of the PDF file (can be passed to next screen for extraction with password)
         */
        fun onPasswordRequired(fileName: String, pdfUri: String) {
            // Default implementation calls onError for backward compatibility
            onError("PDF is password-protected. A password is required.")
        }
    }

    private var callback: PDFSelectionCallback? = null

    /**
     * Set the callback for PDF selection results
     * Use this when you want to manage startActivityForResult yourself
     */
    fun setCallback(callback: PDFSelectionCallback) {
        this.callback = callback
    }

    /**
     * Get intent for PDF file selection
     * Caller should use this with Fragment.startActivityForResult or Activity.startActivityForResult
     */
    fun createPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"  // Filter for PDF files only

            // Optional: Allow multiple file types
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/x-pdf"
            ))
        }
    }

    /**
     * Open document picker for PDF file selection
     * @param callback Callback to receive selection results
     */
    fun openDocumentPicker(callback: PDFSelectionCallback) {
        logd(TAG, "Opening PDF document picker")
        this.callback = callback

        val intent = createPickerIntent()

        try {
            activity.startActivityForResult(intent, PICK_PDF_REQUEST)
            logd(TAG, "Document picker activity started successfully")
        } catch (e: Exception) {
            loge(TAG, "Failed to open document picker: ${e.message}")
            callback.onError("Failed to open document picker: ${e.message}")
        }
    }

    /**
     * Handle activity result from file picker
     * Call this from your activity's onActivityResult
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PICK_PDF_REQUEST && callback != null) {
            if (resultCode == Activity.RESULT_OK && data?.data != null) {
                val uri = data.data!!
                logd(TAG, "PDF file selected: $uri")
                processSelectedFile(uri)
            } else {
                logd(TAG, "PDF selection cancelled by user")
                callback?.onCancelled()
                callback = null
            }
        }
    }

    /**
     * Process the selected PDF file
     * Runs on background thread using coroutines
     */
    private fun processSelectedFile(uri: Uri) {
        logd(TAG, "Starting PDF processing on background thread")
        // Process on background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contentResolver = activity.contentResolver

                // Get file name
                val fileName = getFileName(uri) ?: "unknown.pdf"
                logd(TAG, "PDF file name: $fileName")

                // Verify it's a PDF file
                val mimeType = contentResolver.getType(uri)
                logd(TAG, "File MIME type: $mimeType")
                if (mimeType != "application/pdf") {
                    logw(TAG, "Selected file is not a PDF (MIME: $mimeType)")
                    withContext(Dispatchers.Main) {
                        callback?.onError("Selected file is not a PDF")
                        callback = null
                    }
                    return@launch
                }

                // Create temporary file for PDFBox processing
                logd(TAG, "Creating temporary file from URI")
                val tempFile = createTempFileFromUri(uri)
                if (tempFile != null) {
                    logd(TAG, "Temp file created: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")

                    // Parse PDF
                    logd(TAG, "Starting PDF extraction with BlocktoPDFExtractor")
                    val extractor = BlocktoPDFExtractor(activity.applicationContext)
                    val result = try {
                        extractor.extractJsonFromPdf(tempFile)
                    } catch (e: PasswordRequiredException) {
                        logw(TAG, "PDF is password-protected: ${e.message}")
                        // Copy temp file to a persistent cache location before passing
                        val persistentCacheFile = File(activity.cacheDir, "password_protected_pdf_${System.currentTimeMillis()}.pdf")
                        tempFile.copyTo(persistentCacheFile, overwrite = true)
                        logd(TAG, "Copied password-protected PDF to: ${persistentCacheFile.absolutePath}")
                        
                        withContext(Dispatchers.Main) {
                            // Call the password required callback with cache file path
                            callback?.onPasswordRequired(fileName, persistentCacheFile.absolutePath)
                            callback = null
                        }
                        return@launch
                    } catch (e: PasswordIncorrectException) {
                        logw(TAG, "Password is incorrect: ${e.message}")
                        withContext(Dispatchers.Main) {
                            callback?.onError("Password is incorrect. Please check your password and try again.")
                            callback = null
                        }
                        return@launch
                    } catch (e: Exception) {
                        loge(TAG, "PDF extraction error: ${e.message}")
                        withContext(Dispatchers.Main) {
                            callback?.onError("Failed to extract JSON from PDF: ${e.message ?: "Unknown error"}")
                            callback = null
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        if (result != null) {
                            logd(TAG, "PDF extraction successful, JSON length: ${result.length} characters")
                            callback?.onSuccess(result, fileName)
                        } else {
                            loge(TAG, "PDF extraction returned null result")
                            callback?.onError("Failed to extract JSON from PDF")
                        }
                        callback = null
                    }

                    // Clean up temp file
                    val deleted = tempFile.delete()
                    logd(TAG, "Temp file cleanup: ${if (deleted) "success" else "failed"}")
                } else {
                    loge(TAG, "Failed to create temporary file from URI")
                    withContext(Dispatchers.Main) {
                        callback?.onError("Could not access selected file")
                        callback = null
                    }
                }

            } catch (e: Exception) {
                loge(TAG, "Exception during PDF processing: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback?.onError("Error processing file: ${e.message}")
                    callback = null
                }
            }
        }
    }

    /**
     * Get the display name of a file from its URI
     */
    private fun getFileName(uri: Uri): String? {
        return try {
            activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Create a temporary file from content URI
     * Required for PDFBox which needs a File object
     */
    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            logd(TAG, "Opening input stream from URI")
            val inputStream = activity.contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("blocto_pdf", ".pdf", activity.cacheDir)
            logd(TAG, "Copying file content to temp file")

            inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    val bytesCopied = input.copyTo(output)
                    logd(TAG, "Copied $bytesCopied bytes to temp file")
                }
            }
            tempFile
        } catch (e: Exception) {
            loge(TAG, "Failed to create temp file from URI: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
