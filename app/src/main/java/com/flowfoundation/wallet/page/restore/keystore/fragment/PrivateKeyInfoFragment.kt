package com.flowfoundation.wallet.page.restore.keystore.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentPrivateKeyInfoBinding
import com.flowfoundation.wallet.page.restore.keystore.KeyStoreRestoreActivity
import com.flowfoundation.wallet.page.restore.keystore.viewmodel.KeyStoreRestoreViewModel
import com.flowfoundation.wallet.page.restore.keystore.dialog.PdfPasswordDialog
import com.flowfoundation.wallet.pdfparser.BlocktoPDFExtractor
import com.flowfoundation.wallet.pdfparser.DocumentPickerManager
import com.flowfoundation.wallet.pdfparser.PasswordIncorrectException
import com.flowfoundation.wallet.utils.listeners.SimpleTextWatcher
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toast
import com.instabug.library.Instabug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class PrivateKeyInfoFragment: Fragment() {
    companion object {
        private const val TAG = "PDF_IMPORT"
    }

    private lateinit var binding: FragmentPrivateKeyInfoBinding
    private lateinit var documentPicker: DocumentPickerManager
    private val restoreViewModel by lazy {
        ViewModelProvider(requireActivity())[KeyStoreRestoreViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPrivateKeyInfoBinding.inflate(inflater)
        return binding.root
    }

    private fun canRestore(): Boolean {
        val private = binding.etPrivateKey.text.toString().trim()
        return validatePrivateKey(private)
    }

    private fun validatePrivateKey(input: String): Boolean {
        val privateKeyRegex = Regex("^(0x)?[0-9a-fA-F]{64}$")
        return privateKeyRegex.matches(input)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Initialize document picker
        documentPicker = DocumentPickerManager(requireActivity())

        // Check for pre-filled private key from PDF extraction
        val prefilledPrivateKey = arguments?.getString("private_key")
        val prefilledAddress = arguments?.getString("address")

        with(binding) {
            // Hide password fields - we use a dialog now
            tvPdfPasswordInfo.visibility = View.GONE
            tvPassword.visibility = View.GONE
            tvPasswordAsterisk.visibility = View.GONE
            tilPassword.visibility = View.GONE

            // Ensure private key field and label are visible
            tvPrivateKey.visibility = View.VISIBLE
            tvPrivateKeyAsterisk.visibility = View.VISIBLE
            etPrivateKey.visibility = View.VISIBLE

            // Pre-fill private key if provided
            if (!prefilledPrivateKey.isNullOrEmpty()) {
                etPrivateKey.setText(prefilledPrivateKey)
                logd(TAG, "Pre-filled private key from PDF extraction")
                toast(msg = getString(com.flowfoundation.wallet.R.string.pdf_private_key_extracted))
            }

            // Show PDF import button
            btnImportFromPdf.visibility = View.VISIBLE
            btnImportFromPdf.setOnClickListener {
                openPDFPicker()
            }

            // Show address field
            tvAddress.visibility = View.VISIBLE
            etAddress.visibility = View.VISIBLE

            // Pre-fill address if provided
            if (!prefilledAddress.isNullOrEmpty()) {
                etAddress.setText(prefilledAddress)
            }

            // Set import button text
            btnImport.text = getString(com.flowfoundation.wallet.R.string.import_str)

            etPrivateKey.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    updateImportButtonState()
                }
            })

            btnImport.setOnClickListener {
                val inputText = etPrivateKey.text.toString().trim()

                if (validatePrivateKey(inputText)) {
                    restoreViewModel.importPrivateKey(
                        inputText,
                        etAddress.text.toString().trim()
                    )
                }
            }

            updateImportButtonState()
            Instabug.addPrivateViews(etPrivateKey)
        }
    }

    private fun updateImportButtonState() {
        with(binding) {
            btnImport.isEnabled = canRestore()
        }
    }

    /**
     * Open PDF file picker
     */
    private fun openPDFPicker() {
        logd(TAG, "openPDFPicker called in PrivateKeyInfoFragment")
        val callback = object : DocumentPickerManager.PDFSelectionCallback {
            override fun onSuccess(jsonData: String, fileName: String) {
                // Non-password-protected PDF with keystore JSON
                // Route to keystore page with the pre-extracted JSON
                logd(TAG, "Non-password-protected PDF selected, routing to keystore page with extracted JSON")
                KeyStoreRestoreActivity.launchKeyStore(requireContext(), jsonData)
                activity?.finish()
            }

            override fun onError(error: String) {
                loge(TAG, "PDF parsing failed: $error")
                toast(msg = error)
            }

            override fun onCancelled() {
                // User cancelled, no action needed
            }

            override fun onPasswordRequired(fileName: String, pdfFilePath: String) {
                logd(TAG, "Password-protected PDF detected: $fileName, path: $pdfFilePath")
                // Show password dialog
                showPasswordDialog(pdfFilePath)
            }
        }

        documentPicker.setCallback(callback)

        try {
            val intent = documentPicker.createPickerIntent()
            logd(TAG, "Starting PDF picker from Fragment")
            startActivityForResult(intent, DocumentPickerManager.PICK_PDF_REQUEST)
        } catch (e: Exception) {
            loge(TAG, "Failed to start PDF picker: ${e.message}")
            callback.onError("Failed to open document picker: ${e.message}")
        }
    }

    /**
     * Handle activity result from document picker
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        logd(TAG, "PrivateKeyInfoFragment onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DocumentPickerManager.PICK_PDF_REQUEST && resultCode == Activity.RESULT_OK) {
            logd(TAG, "Passing result to DocumentPickerManager")
            documentPicker.handleActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     * Show password dialog for password-protected PDF
     */
    private fun showPasswordDialog(pdfFilePath: String) {
        PdfPasswordDialog(
            context = requireContext(),
            pdfFilePath = pdfFilePath,
            onUnlock = { password ->
                extractPrivateKeyFromPasswordProtectedPdf(pdfFilePath, password)
            },
            onCancel = {
                // User cancelled, no action needed
            }
        ).show()
    }

    /**
     * Extract private key from password-protected PDF
     * 1. Extract JSON from PDF using password
     * 2. Decrypt keystore JSON using same password to get private key
     * 3. Import the private key
     */
    private fun extractPrivateKeyFromPasswordProtectedPdf(pdfUri: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            var pdfFile: File? = null
            var shouldDeleteFile = false
            try {
                logd(TAG, "Extracting private key from password-protected PDF: $pdfUri")

                // Check if pdfUri is a file path or a content URI
                pdfFile = if (pdfUri.startsWith("/") || pdfUri.startsWith("file://")) {
                    // It's a file path (from cache)
                    val path = if (pdfUri.startsWith("file://")) pdfUri.removePrefix("file://") else pdfUri
                    File(path)
                } else {
                    // It's a content URI - need to copy to temp file
                    shouldDeleteFile = true
                    val uri = Uri.parse(pdfUri)
                    val contentResolver = requireContext().contentResolver
                    val inputStream = contentResolver.openInputStream(uri)
                    val tempFile = File.createTempFile("pdf_keystore", ".pdf", requireContext().cacheDir)
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile
                }

                if (pdfFile == null || !pdfFile.exists()) {
                    withContext(Dispatchers.Main) {
                        toast(msg = getString(com.flowfoundation.wallet.R.string.pdf_file_not_found))
                    }
                    return@launch
                }

                // Extract JSON from PDF with password
                logd(TAG, "Creating BlocktoPDFExtractor and calling extractJsonFromPdf")
                logd(TAG, "PDF file: ${pdfFile.absolutePath}, exists: ${pdfFile.exists()}, size: ${pdfFile.length()}")
                val extractor = BlocktoPDFExtractor(requireContext().applicationContext)
                logd(TAG, "Extractor created, calling extractJsonFromPdf with password length: ${password.length}")
                val jsonResult = extractor.extractJsonFromPdf(pdfFile, password)
                logd(TAG, "extractJsonFromPdf returned: ${if (jsonResult != null) "JSON (${jsonResult.length} chars)" else "null"}")

                if (jsonResult == null) {
                    withContext(Dispatchers.Main) {
                        toast(msg = getString(com.flowfoundation.wallet.R.string.pdf_extract_json_failed))
                    }
                    return@launch
                }

                logd(TAG, "Successfully extracted JSON from PDF, parsing content")

                // Parse the JSON to determine the format
                val jsonObject = org.json.JSONObject(jsonResult)

                // Check if this is a Blocto-style PDF with direct private_key field
                if (jsonObject.has("private_key")) {
                    val privateKey = jsonObject.getString("private_key")
                    val address = if (jsonObject.has("address")) jsonObject.getString("address") else ""

                    logd(TAG, "Found Blocto-style PDF with private_key, filling fields")

                    withContext(Dispatchers.Main) {
                        // Fill the private key and address fields, let user manually import
                        binding.etPrivateKey.setText(privateKey)
                        binding.etPrivateKey.visibility = View.VISIBLE
                        binding.tvPrivateKey.visibility = View.VISIBLE
                        binding.tvPrivateKeyAsterisk.visibility = View.VISIBLE

                        if (address.isNotEmpty()) {
                            binding.etAddress.setText(address)
                        }
                        binding.etAddress.visibility = View.VISIBLE
                        binding.tvAddress.visibility = View.VISIBLE

                        // Hide password field since we're done with PDF extraction
                        binding.tvPdfPasswordInfo.visibility = View.GONE
                        binding.tvPassword.visibility = View.GONE
                        binding.tvPasswordAsterisk.visibility = View.GONE
                        binding.tilPassword.visibility = View.GONE

                        // Update button text back to normal import
                        binding.btnImport.text = getString(com.flowfoundation.wallet.R.string.import_str)

                        // Clear the PDF URI so normal import flow is used
                        arguments?.remove("pdf_uri")

                        // Update button state
                        updateImportButtonState()

                        toast(msg = getString(com.flowfoundation.wallet.R.string.pdf_private_key_extracted))
                    }
                } else if (jsonObject.has("crypto") || jsonObject.has("version")) {
                    // This is a keystore JSON format - route to keystore page
                    logd(TAG, "Found keystore-style PDF, routing to keystore page")

                    withContext(Dispatchers.Main) {
                        toast(msg = getString(com.flowfoundation.wallet.R.string.pdf_contains_keystore))
                    }
                } else {
                    loge(TAG, "Unknown JSON format in PDF (not logging content for security)")
                    withContext(Dispatchers.Main) {
                        toast(msg = getString(com.flowfoundation.wallet.R.string.pdf_unknown_format))
                    }
                }

            } catch (e: PasswordIncorrectException) {
                withContext(Dispatchers.Main) {
                    toast(msg = getString(com.flowfoundation.wallet.R.string.pdf_password_incorrect))
                    // Show dialog again for retry
                    showPasswordDialog(pdfUri)
                }
            } catch (e: Exception) {
                loge(TAG, "Error extracting PDF: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    toast(msg = getString(com.flowfoundation.wallet.R.string.pdf_extract_failed, e.message ?: "Unknown error"))
                }
            } finally {
                // Only delete the file if we created it (content URI case) or if it's a cache file
                if (shouldDeleteFile || pdfFile?.absolutePath?.contains("password_protected_pdf") == true) {
                    pdfFile?.delete()
                }
            }
        }
    }

}
