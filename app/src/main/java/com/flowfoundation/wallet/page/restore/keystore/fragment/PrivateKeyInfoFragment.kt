package com.flowfoundation.wallet.page.restore.keystore.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentPrivateKeyInfoBinding
import com.flowfoundation.wallet.page.restore.keystore.viewmodel.KeyStoreRestoreViewModel
import com.flowfoundation.wallet.page.restore.keystore.KeyStoreRestoreActivity
import com.flowfoundation.wallet.pdfparser.BlocktoPDFExtractor
import com.flowfoundation.wallet.pdfparser.DocumentPickerManager
import com.flowfoundation.wallet.pdfparser.PasswordRequiredException
import com.flowfoundation.wallet.pdfparser.PasswordIncorrectException
import com.flowfoundation.wallet.utils.listeners.SimpleTextWatcher
import com.flowfoundation.wallet.utils.toast
import com.instabug.library.Instabug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class PrivateKeyInfoFragment: Fragment() {
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
        val pdfUri = arguments?.getString("pdf_uri")
        
        // Initialize document picker
        documentPicker = DocumentPickerManager(requireActivity())
        
        with(binding) {
            // Show password field and hide private key field if PDF URI is present
            // Get reference to private key asterisk
            val tvPrivateKeyAsterisk = view.findViewById<View>(com.flowfoundation.wallet.R.id.tv_private_key_asterisk)
            
            if (pdfUri != null) {
                android.util.Log.d("PDF_IMPORT", "PrivateKeyInfoFragment: PDF URI found, showing password field")
                // Hide private key field and label
                val tvPrivateKeyLabel = view.findViewById<View>(com.flowfoundation.wallet.R.id.tv_private_key)
                tvPrivateKeyLabel?.visibility = View.GONE
                tvPrivateKeyAsterisk?.visibility = View.GONE
                etPrivateKey.visibility = View.GONE
                btnImportFromPdf.visibility = View.GONE
                
                // Show password info message and password field
                tvPdfPasswordInfo.visibility = View.VISIBLE
                tvPassword.visibility = View.VISIBLE
                tvPasswordAsterisk.visibility = View.VISIBLE
                tilPassword.visibility = View.VISIBLE
                
                // Update password field hint to be PDF-specific
                etPassword.hint = "Enter your PDF password"
                
                // Hide address field - not needed for PDF password entry
                tvAddress.visibility = View.GONE
                etAddress.visibility = View.GONE
                
                // Update import button to extract from PDF
                btnImport.text = "Extract Private Key from PDF"
            } else {
                // Normal private key flow - ensure password field and info are hidden
                tvPdfPasswordInfo.visibility = View.GONE
                tvPassword.visibility = View.GONE
                tvPasswordAsterisk.visibility = View.GONE
                tilPassword.visibility = View.GONE
                
                // Ensure private key field and label are visible
                val tvPrivateKeyLabel = view.findViewById<View>(com.flowfoundation.wallet.R.id.tv_private_key)
                tvPrivateKeyLabel?.visibility = View.VISIBLE
                tvPrivateKeyAsterisk?.visibility = View.VISIBLE
                etPrivateKey.visibility = View.VISIBLE
                
                // Show PDF import button for normal flow
                btnImportFromPdf.visibility = View.VISIBLE
                btnImportFromPdf.setOnClickListener {
                    openPDFPicker()
                }
                
                // Show address field for normal flow
                tvAddress.visibility = View.VISIBLE
                etAddress.visibility = View.VISIBLE
                
                // Reset import button text
                btnImport.text = getString(com.flowfoundation.wallet.R.string.import_str)
            }
            
            etPrivateKey.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    updateImportButtonState()
                }
            })
            
            etPassword.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    updateImportButtonState()
                }
            })
            
            btnImport.setOnClickListener {
                // Re-read pdfUri from arguments in case it was updated
                val currentPdfUri = arguments?.getString("pdf_uri")
                val inputText = etPrivateKey.text.toString().trim()
                val password = etPassword.text?.toString()?.trim() ?: ""
                
                android.util.Log.d("PDF_IMPORT", "Import button clicked - pdfUri: $currentPdfUri, password length: ${password.length}")
                
                if (currentPdfUri != null && password.isNotEmpty()) {
                    // Extract from password-protected PDF
                    android.util.Log.d("PDF_IMPORT", "Calling extractPrivateKeyFromPasswordProtectedPdf")
                    extractPrivateKeyFromPasswordProtectedPdf(currentPdfUri, password)
                } else if (validatePrivateKey(inputText)) {
                    // Normal private key import flow
                    restoreViewModel.importPrivateKey(
                        inputText,
                        etAddress.text.toString().trim()
                    )
                } else {
                    android.util.Log.d("PDF_IMPORT", "Button click - no action taken. pdfUri: $currentPdfUri, password empty: ${password.isEmpty()}, validPrivateKey: ${validatePrivateKey(inputText)}")
                }
            }
            
            updateImportButtonState()
            Instabug.addPrivateViews(etPrivateKey)
            Instabug.addPrivateViews(etPassword)
        }
    }
    
    private fun updateImportButtonState() {
        val pdfUri = arguments?.getString("pdf_uri")
        with(binding) {
            if (pdfUri != null) {
                // Enable if password is entered
                btnImport.isEnabled = etPassword.text?.toString()?.trim()?.isNotEmpty() == true
            } else {
                // Enable if private key is valid
                btnImport.isEnabled = canRestore()
            }
        }
    }
    
    /**
     * Open PDF file picker
     */
    private fun openPDFPicker() {
        android.util.Log.d("PDF_IMPORT", "openPDFPicker called in PrivateKeyInfoFragment")
        val callback = object : DocumentPickerManager.PDFSelectionCallback {
            override fun onSuccess(jsonData: String, fileName: String) {
                // For private key page, we need to extract private key from keystore JSON
                // But we don't have password here, so route to keystore page instead
                android.util.Log.d("PDF_IMPORT", "Non-password-protected PDF selected, routing to keystore page")
                KeyStoreRestoreActivity.launchKeyStore(requireContext())
                activity?.finish()
            }

            override fun onError(error: String) {
                android.util.Log.e("PDF_IMPORT", "PDF parsing failed: $error")
                toast(msg = error)
            }

            override fun onCancelled() {
                // User cancelled, no action needed
            }

            override fun onPasswordRequired(fileName: String, pdfUri: String) {
                android.util.Log.d("PDF_IMPORT", "Password-protected PDF detected: $fileName, URI: $pdfUri")
                // Already on private key page, just update the PDF URI
                val args = Bundle().apply {
                    putString("pdf_uri", pdfUri)
                }
                arguments = args
                
                // Hide private key field and label, show password field
                view?.let { v ->
                    v.findViewById<View>(com.flowfoundation.wallet.R.id.tv_private_key)?.visibility = View.GONE
                    v.findViewById<View>(com.flowfoundation.wallet.R.id.tv_private_key_asterisk)?.visibility = View.GONE
                }
                
                with(binding) {
                    etPrivateKey.visibility = View.GONE
                    btnImportFromPdf.visibility = View.GONE
                    
                    // Show password info message and password field
                    tvPdfPasswordInfo.visibility = View.VISIBLE
                    tvPassword.visibility = View.VISIBLE
                    tvPasswordAsterisk.visibility = View.VISIBLE
                    tilPassword.visibility = View.VISIBLE
                    etPassword.hint = "Enter your PDF password"
                    
                    // Hide address field - not needed for PDF password entry
                    tvAddress.visibility = View.GONE
                    etAddress.visibility = View.GONE
                    
                    btnImport.text = "Extract Private Key from PDF"
                    updateImportButtonState()
                }
            }
        }

        documentPicker.setCallback(callback)

        try {
            val intent = documentPicker.createPickerIntent()
            android.util.Log.d("PDF_IMPORT", "Starting PDF picker from Fragment")
            startActivityForResult(intent, DocumentPickerManager.PICK_PDF_REQUEST)
        } catch (e: Exception) {
            android.util.Log.e("PDF_IMPORT", "Failed to start PDF picker", e)
            callback.onError("Failed to open document picker: ${e.message}")
        }
    }
    
    /**
     * Handle activity result from document picker
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        android.util.Log.d("PDF_IMPORT", "PrivateKeyInfoFragment onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DocumentPickerManager.PICK_PDF_REQUEST && resultCode == Activity.RESULT_OK) {
            android.util.Log.d("PDF_IMPORT", "Passing result to DocumentPickerManager")
            documentPicker.handleActivityResult(requestCode, resultCode, data)
        }
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
                android.util.Log.d("PDF_IMPORT", "Extracting private key from password-protected PDF: $pdfUri")
                
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
                        toast(msg = "PDF file not found")
                    }
                    return@launch
                }
                
                // Extract JSON from PDF with password
                android.util.Log.d("PDF_IMPORT", "Creating BlocktoPDFExtractor and calling extractJsonFromPdf")
                android.util.Log.d("PDF_IMPORT", "PDF file: ${pdfFile.absolutePath}, exists: ${pdfFile.exists()}, size: ${pdfFile.length()}")
                val extractor = BlocktoPDFExtractor(requireContext().applicationContext)
                android.util.Log.d("PDF_IMPORT", "Extractor created, calling extractJsonFromPdf with password length: ${password.length}")
                val jsonResult = extractor.extractJsonFromPdf(pdfFile, password)
                android.util.Log.d("PDF_IMPORT", "extractJsonFromPdf returned: ${if (jsonResult != null) "JSON (${jsonResult.length} chars)" else "null"}")
                
                if (jsonResult == null) {
                    withContext(Dispatchers.Main) {
                        toast(msg = "Failed to extract JSON from PDF")
                    }
                    return@launch
                }
                
                android.util.Log.d("PDF_IMPORT", "Successfully extracted JSON from PDF, now decrypting keystore")
                
                // Use ViewModel to import keystore (which will decrypt and extract private key)
                withContext(Dispatchers.Main) {
                    restoreViewModel.importKeyStore(
                        jsonResult,
                        password, // Use same password for PDF and keystore
                        binding.etAddress.text.toString().trim()
                    )
                }
                
            } catch (e: PasswordIncorrectException) {
                withContext(Dispatchers.Main) {
                    toast(msg = "Password is incorrect. Please check your password and try again.")
                }
            } catch (e: Exception) {
                android.util.Log.e("PDF_IMPORT", "Error extracting PDF: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    toast(msg = "Failed to extract PDF: ${e.message ?: "Unknown error"}")
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