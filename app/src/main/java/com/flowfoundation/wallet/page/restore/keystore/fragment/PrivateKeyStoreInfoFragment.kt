package com.flowfoundation.wallet.page.restore.keystore.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentPrivateKeyStoreInfoBinding
import com.flowfoundation.wallet.page.restore.keystore.viewmodel.KeyStoreRestoreViewModel
import com.flowfoundation.wallet.page.restore.keystore.KeyStoreRestoreActivity
import com.flowfoundation.wallet.page.restore.keystore.dialog.PdfPasswordDialog
import com.flowfoundation.wallet.pdfparser.BlocktoPDFExtractor
import com.flowfoundation.wallet.pdfparser.DocumentPickerManager
import com.flowfoundation.wallet.pdfparser.PasswordIncorrectException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.flowfoundation.wallet.utils.listeners.SimpleTextWatcher
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toast
import com.instabug.library.Instabug
import org.json.JSONObject


class PrivateKeyStoreInfoFragment: Fragment() {
    companion object {
        private const val TAG = "PDF_IMPORT"
    }

    private lateinit var binding: FragmentPrivateKeyStoreInfoBinding
    private val restoreViewModel by lazy {
        ViewModelProvider(requireActivity())[KeyStoreRestoreViewModel::class.java]
    }
    private lateinit var documentPicker: DocumentPickerManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPrivateKeyStoreInfoBinding.inflate(inflater)
        return binding.root
    }

    private fun canRestore(): Boolean {
        val json = binding.etJson.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        return isValidJson(json) && password.isNotEmpty()
    }

    private fun isValidJson(input: String): Boolean {
        return try {
            JSONObject(input)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Initialize document picker
        documentPicker = DocumentPickerManager(requireActivity())

        // Check if keystore JSON was passed from another screen (e.g., PDF extracted from private key screen)
        val prefilledJson = arguments?.getString("keystore_json")
        if (prefilledJson != null) {
            binding.etJson.setText(prefilledJson)
            toast(msg = getString(R.string.pdf_keystore_extracted))
        }

        with(binding) {
            etJson.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    val jsonText = s.toString().trim()

                    // Validate JSON and show/hide error message
                    if (jsonText.isNotEmpty() && !isValidJson(jsonText)) {
                        // Show error for invalid JSON
                        tvPdfError.visibility = View.VISIBLE
                    } else {
                        // Hide error for valid JSON or empty field
                        tvPdfError.visibility = View.GONE
                    }

                    btnImport.isEnabled = canRestore()
                }
            })
            etPassword.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    btnImport.isEnabled = canRestore()
                }
            })
            btnImport.setOnClickListener {
                // Hide error message before attempting import
                tvPdfError.visibility = View.GONE
                restoreViewModel.importKeyStore(
                    etJson.text.toString().trim(),
                    etPassword.text.toString().trim(),
                    etAddress.text.toString().trim()
                )
            }
            btnImport.isEnabled = false

            // Set up PDF import button
            btnImportFromPdf.setOnClickListener {
                openPDFPicker()
            }

            // Set up clickable error message
            setupErrorMessage()

            Instabug.addPrivateViews(etJson)
            Instabug.addPrivateViews(etPassword)
        }

        // Observe keystore format errors from ViewModel
        restoreViewModel.keystoreFormatErrorLiveData.observe(viewLifecycleOwner) { showError ->
            if (showError) {
                binding.tvPdfError.visibility = View.VISIBLE
                // Reset the LiveData value to allow showing the error again
                restoreViewModel.keystoreFormatErrorLiveData.value = false
            }
        }
    }

    /**
     * Setup the error message with clickable link to Chrome Web Store
     */
    private fun setupErrorMessage() {
        val errorMessage = getString(R.string.pdf_parse_error_message)
        val extensionUrl = getString(R.string.extension_download_url)
        val linkText = "Flow Wallet Extension"

        // Find the position of the link text in the error message
        val startIndex = errorMessage.indexOf(linkText)

        if (startIndex >= 0) {
            val spannableString = SpannableString(errorMessage)
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    // Open browser with Chrome Web Store link
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(extensionUrl))
                    startActivity(intent)
                }

                override fun updateDrawState(ds: android.text.TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = true
                    ds.color = ContextCompat.getColor(requireContext(), R.color.info_error_red)
                }
            }

            spannableString.setSpan(
                clickableSpan,
                startIndex,
                startIndex + linkText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            binding.tvPdfError.text = spannableString
            binding.tvPdfError.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    /**
     * Open PDF file picker
     */
    private fun openPDFPicker() {
        logd(TAG, "openPDFPicker called in Fragment")
        val callback = object : DocumentPickerManager.PDFSelectionCallback {
            override fun onSuccess(jsonData: String, fileName: String) {
                // Populate the JSON field with extracted data
                binding.etJson.setText(jsonData)
                // Hide error message on success
                binding.tvPdfError.visibility = View.GONE
                toast(msg = getString(R.string.pdf_imported_success, fileName))
            }

            override fun onError(error: String) {
                loge(TAG, "PDF parsing failed: $error")
                // Show error message instead of toast
                binding.tvPdfError.visibility = View.VISIBLE
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

        // Store callback for later use
        documentPicker.setCallback(callback)

        // Start activity from Fragment (not Activity) to ensure result comes back to Fragment
        try {
            val intent = documentPicker.createPickerIntent()
            logd(TAG, "Starting PDF picker from Fragment with requestCode=${DocumentPickerManager.PICK_PDF_REQUEST}")
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
        logd(TAG, "Fragment onActivityResult: requestCode=$requestCode, resultCode=$resultCode, data=$data")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DocumentPickerManager.PICK_PDF_REQUEST && resultCode == Activity.RESULT_OK) {
            logd(TAG, "Passing result to DocumentPickerManager")
            documentPicker.handleActivityResult(requestCode, resultCode, data)
        } else {
            logd(TAG, "Result not handled: requestCode=${DocumentPickerManager.PICK_PDF_REQUEST}, RESULT_OK=${Activity.RESULT_OK}")
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
                extractJsonFromPasswordProtectedPdf(pdfFilePath, password)
            },
            onCancel = {
                // User cancelled, no action needed
            }
        ).show()
    }

    /**
     * Extract JSON from password-protected PDF
     */
    private fun extractJsonFromPasswordProtectedPdf(pdfFilePath: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            var pdfFile: File? = null
            try {
                logd(TAG, "Extracting JSON from password-protected PDF: $pdfFilePath")

                pdfFile = File(pdfFilePath)

                if (!pdfFile.exists()) {
                    withContext(Dispatchers.Main) {
                        toast(msg = getString(R.string.pdf_file_not_found))
                    }
                    return@launch
                }

                val extractor = BlocktoPDFExtractor(requireContext().applicationContext)
                val jsonResult = extractor.extractJsonFromPdf(pdfFile, password)

                if (jsonResult == null) {
                    withContext(Dispatchers.Main) {
                        toast(msg = getString(R.string.pdf_extract_json_failed))
                    }
                    return@launch
                }

                logd(TAG, "Successfully extracted JSON from PDF")

                // Parse the JSON to determine the format
                val jsonObject = org.json.JSONObject(jsonResult)

                // Check if this is a Blocto-style PDF with direct private_key field
                if (jsonObject.has("private_key")) {
                    val privateKey = jsonObject.getString("private_key")
                    val address = if (jsonObject.has("address")) jsonObject.getString("address") else ""

                    logd(TAG, "Found Blocto-style PDF with private_key, routing to private key page with data")

                    withContext(Dispatchers.Main) {
                        // Route to private key page with the extracted key pre-filled
                        KeyStoreRestoreActivity.launchPrivateKeyWithData(
                            requireContext(),
                            privateKey,
                            address.ifEmpty { null }
                        )
                        activity?.finish()
                    }
                } else if (jsonObject.has("crypto") || jsonObject.has("version")) {
                    // This is a keystore JSON format - fill the field
                    logd(TAG, "Found keystore-style PDF, filling JSON field")

                    withContext(Dispatchers.Main) {
                        binding.etJson.setText(jsonResult)
                        binding.tvPdfError.visibility = View.GONE
                        toast(msg = getString(R.string.pdf_keystore_extracted))
                    }
                } else {
                    loge(TAG, "Unknown JSON format in PDF")
                    withContext(Dispatchers.Main) {
                        toast(msg = getString(R.string.pdf_unknown_format))
                    }
                }

            } catch (e: PasswordIncorrectException) {
                withContext(Dispatchers.Main) {
                    toast(msg = getString(R.string.pdf_password_incorrect))
                    // Show dialog again for retry
                    showPasswordDialog(pdfFilePath)
                }
            } catch (e: Exception) {
                loge(TAG, "Error extracting PDF: ${e.message}")
                withContext(Dispatchers.Main) {
                    toast(msg = getString(R.string.pdf_extract_failed, e.message ?: "Unknown error"))
                }
            } finally {
                // Clean up temp file if it was created in cache
                if (pdfFile?.absolutePath?.contains("password_protected_pdf") == true) {
                    pdfFile.delete()
                    logd(TAG, "Cleaned up temporary PDF file")
                }
            }
        }
    }

}
