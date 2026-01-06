package com.flowfoundation.wallet.pdfparser

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern

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
                Log.d(TAG, "Initializing PDFBox for Android with context")
                // Initialize PDFBox resource loader with Android context
                PDFBoxResourceLoader.init(context)
                isPDFBoxInitialized = true
                Log.d(TAG, "PDFBox initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize PDFBox", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Extract JSON string from a PDF file
     * @param file PDF file to extract from
     * @param pageIndex Zero-based page index (default: 0 for first page)
     * @return Extracted JSON string or null if extraction fails
     */
    fun extractJsonFromPdf(file: File, pageIndex: Int = 0): String? {
        return try {
            Log.d(TAG, "Starting JSON extraction from PDF: ${file.name}")

            // Initialize PDFBox for Android if not already initialized
            if (!isPDFBoxInitialized) {
                initializePDFBox(context)
            }

            // 1. Load PDF document
            Log.d(TAG, "Loading PDF document with PDFBox")
            val document = PDDocument.load(file)
            val pageCount = document.numberOfPages
            Log.d(TAG, "PDF loaded successfully, total pages: $pageCount")

            // 2. Configure text stripper for specific page
            val stripper = PDFTextStripper()
            // PDFBox uses 1-based page indexing
            stripper.startPage = pageIndex + 1
            stripper.endPage = pageIndex + 1
            Log.d(TAG, "Extracting text from page ${pageIndex + 1} of $pageCount")

            // 3. Extract page text
            val pageText = stripper.getText(document)
            document.close()
            Log.d(TAG, "Text extracted, length: ${pageText.length} characters")

            // 4. Extract JSON from text
            Log.d(TAG, "Searching for JSON pattern in extracted text")
            val result = extractJsonString(pageText)
            if (result != null) {
                Log.d(TAG, "JSON extraction successful")
            } else {
                Log.w(TAG, "No valid JSON found in PDF text")
            }
            result

        } catch (e: Exception) {
            Log.e(TAG, "Exception during PDF JSON extraction", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * Extract JSON string from text using regex patterns
     * Tries multiple patterns to find valid JSON, prioritizing keystore-like structures
     */
    private fun extractJsonString(text: String): String? {
        Log.d(TAG, "Attempting to extract JSON from text of length ${text.length}")
        Log.d(TAG, "Text preview (first 500 chars): ${text.take(500)}")
        
        // First, try to find keystore-specific patterns (more reliable for Blocto)
        val keystoreResult = extractKeystoreJson(text)
        if (keystoreResult != null) {
            Log.d(TAG, "Found keystore JSON using keystore-specific extraction")
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

        Log.d(TAG, "Trying ${patterns.size} regex patterns to find JSON")
        for ((index, patternStr) in patterns.withIndex()) {
            try {
                Log.d(TAG, "Trying pattern ${index + 1}/${patterns.size}")
                val pattern = Pattern.compile(patternStr, Pattern.DOTALL)
                val matcher = pattern.matcher(text)

                if (matcher.find()) {
                    val jsonString = matcher.group()
                    Log.d(TAG, "Pattern ${index + 1} matched, found string of length ${jsonString.length}")

                    // Clean up whitespace and newlines
                    val cleanedJson = jsonString
                        .replace(Regex("\\s+"), " ")
                        .trim()

                    Log.d(TAG, "Cleaned JSON length: ${cleanedJson.length}, validating...")
                    // Validate JSON structure
                    if (isValidJSON(cleanedJson)) {
                        Log.d(TAG, "Valid JSON found with pattern ${index + 1}")
                        return cleanedJson
                    } else {
                        Log.d(TAG, "Pattern ${index + 1} match was not valid JSON, trying next pattern")
                    }
                } else {
                    Log.d(TAG, "Pattern ${index + 1} did not match")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception with pattern ${index + 1}: ${e.message}")
                continue
            }
        }
        Log.w(TAG, "No valid JSON found after trying all patterns")
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
            Log.d(TAG, "No opening brace found in text")
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
                    Log.d(TAG, "Found valid keystore JSON at position $currentStart")
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
            Log.d(TAG, "Keystore check: version=$hasVersion, crypto=$hasCrypto, id=$hasId -> $result")
            result
        } catch (e: Exception) {
            Log.d(TAG, "Keystore check failed with exception: ${e.message}")
            false
        }
    }

    /**
     * Validate if a string is valid JSON (either object or array)
     */
    private fun isValidJSON(jsonString: String): Boolean {
        return try {
            Log.d(TAG, "Validating JSON as JSONObject")
            JSONObject(jsonString)
            Log.d(TAG, "Valid JSONObject")
            true
        } catch (e: org.json.JSONException) {
            try {
                Log.d(TAG, "Not a JSONObject, trying JSONArray")
                JSONArray(jsonString)
                Log.d(TAG, "Valid JSONArray")
                true
            } catch (e2: org.json.JSONException) {
                Log.w(TAG, "Invalid JSON: ${e2.message}")
                false
            }
        }
    }
}
