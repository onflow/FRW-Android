package com.flowfoundation.wallet.pdfparser

import android.content.Context
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logw
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern

/**
 * Exception thrown when a PDF is encrypted but no password was provided
 */
class PasswordRequiredException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when the provided password is incorrect
 */
class PasswordIncorrectException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Extract JSON data from Blocto RecoveryKit PDF files
 */
class BlocktoPDFExtractor(private val context: Context) {

    companion object {
        private const val TAG = "PDF_IMPORT"
        private var isPDFBoxInitialized = false

        /**
         * Initialize PDFBox resources for Android
         * @param context Android context required for resource loading
         */
        private fun initializePDFBox(context: Context) {
            try {
                logd(TAG, "Initializing PDFBox for Android with context")
                // Initialize PDFBox resource loader with Android context
                PDFBoxResourceLoader.init(context)
                isPDFBoxInitialized = true
                logd(TAG, "PDFBox initialized successfully")
            } catch (e: Exception) {
                loge(TAG, "Failed to initialize PDFBox: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Extract JSON string from a PDF file
     * Supports both encrypted and unencrypted PDFs
     * @param file PDF file to extract from
     * @param password Optional password for encrypted PDFs. If null and PDF is encrypted, will throw PasswordRequiredException
     * @param pageIndex Zero-based page index (default: 0 for first page)
     * @return Extracted JSON string or null if extraction fails
     * @throws PasswordRequiredException if PDF is encrypted and no password provided
     * @throws PasswordIncorrectException if provided password is incorrect
     */
    fun extractJsonFromPdf(file: File, password: String? = null, pageIndex: Int = 0): String? {
        var document: PDDocument? = null
        return try {
            logd(TAG, "Starting JSON extraction from PDF: ${file.name}")

            // Initialize PDFBox for Android if not already initialized
            if (!isPDFBoxInitialized) {
                initializePDFBox(context)
            }

            // 1. Load PDF document (with or without password)
            logd(TAG, if (password != null) "Loading password-protected PDF document" else "Loading PDF document")
            
            document = if (password != null) {
                // Try loading with password
                try {
                    logd(TAG, "Attempting to load PDF with password (length: ${password.length})")
                    // Use InputStream instead of File for better Android compatibility
                    val inputStream = file.inputStream()
                    logd(TAG, "Created InputStream, calling PDDocument.load with password")
                    val loadedDoc = PDDocument.load(inputStream, password)
                    logd(TAG, "PDDocument.load completed successfully")
                    // Note: isEncrypted returns true even after successful decryption
                    // It just indicates the document WAS encrypted, not that decryption failed
                    // If password was wrong, PDFBox would have thrown an exception during load()
                    logd(TAG, "PDF loaded and decrypted successfully (isEncrypted=${loadedDoc.isEncrypted})")
                    loadedDoc
                } catch (e: PasswordIncorrectException) {
                    // Re-throw password incorrect exceptions
                    loge(TAG, "PasswordIncorrectException: ${e.message}")
                    throw e
                } catch (e: Exception) {
                    // Check if it's a password-related error
                    loge(TAG, "Exception during PDF load with password: ${e.javaClass.simpleName}: ${e.message}")
                    e.printStackTrace()
                    val errorMsg = e.message?.lowercase() ?: ""
                    val className = e.javaClass.simpleName.lowercase()
                    if (errorMsg.contains("password") || 
                        errorMsg.contains("incorrect") ||
                        errorMsg.contains("wrong") ||
                        errorMsg.contains("invalid password") ||
                        className.contains("password") ||
                        className.contains("encryption")) {
                        throw PasswordIncorrectException("Password is incorrect. Please check your password and try again.", e)
                    }
                    throw e
                }
            } else {
                // Try loading without password
                try {
                    val loadedDoc = PDDocument.load(file)
                    // Check if document is encrypted even if load succeeded
                    if (loadedDoc.isEncrypted) {
                        loadedDoc.close()
                        logd(TAG, "PDF is encrypted but no password provided")
                        throw PasswordRequiredException("PDF is password-protected. A password is required to decrypt this file.")
                    }
                    loadedDoc
                } catch (e: PasswordRequiredException) {
                    // Re-throw password required exceptions
                    throw e
                } catch (e: Exception) {
                    // Check if PDF is encrypted (requires password)
                    val errorMsg = e.message?.lowercase() ?: ""
                    val className = e.javaClass.simpleName.lowercase()
                    if (errorMsg.contains("password") || 
                        errorMsg.contains("encrypted") ||
                        errorMsg.contains("decrypt") ||
                        errorMsg.contains("encryption") ||
                        className.contains("encryption") ||
                        className.contains("password")) {
                        throw PasswordRequiredException("PDF is password-protected. A password is required to decrypt this file.", e)
                    }
                    throw e
                }
            }
            
            val pageCount = document.numberOfPages
            logd(TAG, "PDF loaded successfully, total pages: $pageCount")

            // 2. Configure text stripper for specific page
            val stripper = PDFTextStripper()
            // PDFBox uses 1-based page indexing
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            logd(TAG, "Extracting text from page ${pageIndex + 1} of $pageCount")

            // 3. Extract page text
            val pageText = stripper.getText(document)
            logd(TAG, "Text extracted, length: ${pageText.length} characters")

            // 4. Extract JSON from text
            logd(TAG, "Searching for JSON pattern in extracted text")
            val result = extractJsonString(pageText)
            if (result != null) {
                logd(TAG, "JSON extraction successful")
            } else {
                logw(TAG, "No valid JSON found in PDF text")
            }
            result

        } catch (e: PasswordRequiredException) {
            // Re-throw password required exceptions
            throw e
        } catch (e: PasswordIncorrectException) {
            // Re-throw password incorrect exceptions
            throw e
        } catch (e: Exception) {
            loge(TAG, "Exception during PDF JSON extraction: ${e.message}")
            e.printStackTrace()
            // Wrap other exceptions
            throw IllegalArgumentException("Failed to extract JSON from PDF: ${e.message ?: "Unknown error"}", e)
        } finally {
            // Ensure document is always closed
            try {
                document?.close()
            } catch (e: Exception) {
                logw(TAG, "Error closing PDF document: ${e.message}")
            }
        }
    }


    /**
     * Extract JSON string from text using regex patterns
     * Tries multiple patterns to find valid JSON, prioritizing keystore-like structures
     */
    private fun extractJsonString(text: String): String? {
        logd(TAG, "Attempting to extract JSON from text of length ${text.length}")
        // Note: Not logging text content to avoid exposing private keys
        
        // First, try to find keystore-specific patterns (more reliable for Blocto)
        val keystoreResult = extractKeystoreJson(text)
        if (keystoreResult != null) {
            logd(TAG, "Found keystore JSON using keystore-specific extraction")
            return keystoreResult
        }
        
        // Fallback to generic JSON extraction
        // Multiple regex patterns for better JSON detection
        val patterns = listOf(
            // Complete JSON objects with nested structures
            "\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}",
            // Array of objects
            "\\[[^\\[\\]]*(?:\\{[^{}]*\\}[^\\[\\]]*)*\\]",
            // Fallback: simpler pattern
            "\\{.*\\}"
        )

        logd(TAG, "Trying ${patterns.size} regex patterns to find JSON")
        for ((index, patternStr) in patterns.withIndex()) {
            try {
                logd(TAG, "Trying pattern ${index + 1}/${patterns.size}")
                val pattern = Pattern.compile(patternStr, Pattern.DOTALL)
                val matcher = pattern.matcher(text)

                if (matcher.find()) {
                    val jsonString = matcher.group()
                    logd(TAG, "Pattern ${index + 1} matched, found string of length ${jsonString.length}")

                    // Clean up whitespace and newlines
                    val cleanedJson = jsonString
                        .replace(Regex("\\s+"), " ")
                        .trim()

                    logd(TAG, "Cleaned JSON length: ${cleanedJson.length}, validating...")
                    // Validate JSON structure
                    if (isValidJSON(cleanedJson)) {
                        logd(TAG, "Valid JSON found with pattern ${index + 1}")
                        return cleanedJson
                    } else {
                        logd(TAG, "Pattern ${index + 1} match was not valid JSON, trying next pattern")
                    }
                } else {
                    logd(TAG, "Pattern ${index + 1} did not match")
                }
            } catch (e: Exception) {
                logw(TAG, "Exception with pattern ${index + 1}: ${e.message}")
                continue
            }
        }
        logw(TAG, "No valid JSON found after trying all patterns")
        return null
    }
    
    /**
     * Extract keystore JSON specifically by finding the opening brace and matching braces
     * This handles deeply nested JSON better than regex
     */
    private fun extractKeystoreJson(text: String): String? {
        // Look for keystore-specific markers
        val keystoreMarkers = listOf(
            "\"version\"", "\"crypto\"", "\"Crypto\"", "\"ciphertext\"", "\"kdf\""
        )
        
        // Find the start of potential keystore JSON
        val startIndex = text.indexOf("{")
        if (startIndex == -1) {
            logd(TAG, "No opening brace found in text")
            return null
        }
        
        // Try to extract balanced JSON starting from each opening brace
        var currentStart = startIndex
        while (currentStart != -1 && currentStart < text.length) {
            val extracted = extractBalancedJson(text, currentStart)
            if (extracted != null) {
                // Clean whitespace
                val cleaned = extracted
                    .replace(Regex("\\s+"), " ")
                    .trim()
                
                // Validate it's a proper keystore
                if (isValidJSON(cleaned) && isKeystoreJson(cleaned)) {
                    logd(TAG, "Found valid keystore JSON at position $currentStart")
                    return cleaned
                }
            }
            
            // Look for next opening brace
            currentStart = text.indexOf("{", currentStart + 1)
        }
        
        return null
    }
    
    /**
     * Extract a balanced JSON object starting from the given position
     */
    private fun extractBalancedJson(text: String, start: Int): String? {
        if (start >= text.length || text[start] != '{') return null
        
        var braceCount = 0
        var end = start
        
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        
        return if (braceCount == 0 && end > start) {
            text.substring(start, end + 1)
        } else {
            null
        }
    }
    
    /**
     * Check if a JSON string looks like a keystore (has required keystore fields)
     */
    private fun isKeystoreJson(jsonString: String): Boolean {
        return try {
            val json = JSONObject(jsonString)
            val hasVersion = json.has("version")
            val hasCrypto = json.has("crypto") || json.has("Crypto")
            val hasId = json.has("id")
            
            val result = hasVersion && hasCrypto && hasId
            logd(TAG, "Keystore check: version=$hasVersion, crypto=$hasCrypto, id=$hasId -> $result")
            result
        } catch (e: Exception) {
            logd(TAG, "Keystore check failed with exception: ${e.message}")
            false
        }
    }

    /**
     * Validate if a string is valid JSON (either object or array)
     */
    private fun isValidJSON(jsonString: String): Boolean {
        return try {
            logd(TAG, "Validating JSON as JSONObject")
            JSONObject(jsonString)
            logd(TAG, "Valid JSONObject")
            true
        } catch (e: org.json.JSONException) {
            try {
                logd(TAG, "Not a JSONObject, trying JSONArray")
                JSONArray(jsonString)
                logd(TAG, "Valid JSONArray")
                true
            } catch (e2: org.json.JSONException) {
                logw(TAG, "Invalid JSON: ${e2.message}")
                false
            }
        }
    }
}
