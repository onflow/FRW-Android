package com.flowfoundation.wallet.page.restore.keystore

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import androidx.transition.Fade
import androidx.transition.Transition
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityRestoreKeyStoreBinding
import com.flowfoundation.wallet.page.restore.keystore.fragment.KeyStoreSelectAccountDialog
import com.flowfoundation.wallet.page.restore.keystore.fragment.PrivateKeyInfoFragment
import com.flowfoundation.wallet.page.restore.keystore.fragment.PrivateKeyStoreInfoFragment
import com.flowfoundation.wallet.page.restore.keystore.fragment.SeedPhraseInfoFragment
import com.flowfoundation.wallet.page.restore.keystore.model.KeyStoreOption
import com.flowfoundation.wallet.page.restore.keystore.viewmodel.KeyStoreRestoreViewModel
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.FlowLoadingDialog
import com.google.android.material.transition.MaterialSharedAxis
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX


class KeyStoreRestoreActivity : BaseActivity() {

    private lateinit var restoreViewModel: KeyStoreRestoreViewModel
    private lateinit var binding: ActivityRestoreKeyStoreBinding
    private var currentOption: KeyStoreOption? = null

    private val isPrivateKey by lazy {
        intent.getBooleanExtra(EXTRA_RESTORE_PRIVATE_KEY, false)
    }

    private val isSeedPhrase by lazy {
        intent.getBooleanExtra(EXTRA_RESTORE_SEED_PHRASE, false)
    }

    private val loadingDialog by lazy { FlowLoadingDialog(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRestoreKeyStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.bg_2)
            .light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()
        setupToolbar()
        restoreViewModel = ViewModelProvider(this)[KeyStoreRestoreViewModel::class.java].apply {
            addressListLiveData.observe(this@KeyStoreRestoreActivity) { list ->
                if (list.isNotEmpty()) {
                    uiScope {
                        KeyStoreSelectAccountDialog().show(
                            supportFragmentManager
                        )?.let { address ->
                            restoreViewModel.importKeyStoreAddress(address)
                        }
                    }
                } else {
                    uiScope {
                        // If no existing accounts found, directly create a new one with random username
                        restoreViewModel.createNewAccountFromKeystore()
                    }
                }
            }
            optionChangeLiveData.observe(this@KeyStoreRestoreActivity) {
                onOptionChange(it)
            }
            loadingLiveData.observe(this@KeyStoreRestoreActivity) { show ->
                uiScope {
                    if (show) {
                        loadingDialog.show()
                    } else {
                        loadingDialog.dismiss()
                    }
                }
            }
            changeOption(
                if (isPrivateKey) {
                    KeyStoreOption.INPUT_PRIVATE_KEY_INFO
                } else if (isSeedPhrase) {
                    KeyStoreOption.INPUT_SEED_PHRASE_INFO
                } else {
                    KeyStoreOption.INPUT_KEYSTORE_INFO
                }
            )
        }
    }

    @SuppressLint("CommitTransaction")
    private fun onOptionChange(option: KeyStoreOption) {
        val transition = createTransition(currentOption, option)
        val fragment = when (option) {
            KeyStoreOption.INPUT_KEYSTORE_INFO -> {
                val keystoreJson = intent.getStringExtra(EXTRA_KEYSTORE_JSON)
                PrivateKeyStoreInfoFragment().apply {
                    if (keystoreJson != null) {
                        arguments = Bundle().apply {
                            putString("keystore_json", keystoreJson)
                        }
                    }
                }
            }
            KeyStoreOption.INPUT_PRIVATE_KEY_INFO -> {
                val pdfUri = intent.getStringExtra(EXTRA_PDF_URI)
                val privateKey = intent.getStringExtra(EXTRA_PRIVATE_KEY)
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                PrivateKeyInfoFragment().apply {
                    arguments = Bundle().apply {
                        pdfUri?.let { putString("pdf_uri", it) }
                        privateKey?.let { putString("private_key", it) }
                        address?.let { putString("address", it) }
                    }
                }
            }
            KeyStoreOption.INPUT_SEED_PHRASE_INFO -> SeedPhraseInfoFragment()
            else -> return
        }
        fragment.enterTransition = transition
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        currentOption = option
    }

    private fun createTransition(
        currentOption: KeyStoreOption?,
        option: KeyStoreOption
    ): Transition {
        if (currentOption == null) {
            return Fade().apply { duration = 50 }
        }
        val transition = MaterialSharedAxis(MaterialSharedAxis.X, true)

        transition.addTarget(currentOption.layoutId)
        transition.addTarget(option.layoutId)
        return transition
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                handleBackNavigation()
            }

            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun handleBackNavigation() {
        finish()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        private const val EXTRA_RESTORE_PRIVATE_KEY = "extra_restore_private_key"
        private const val EXTRA_RESTORE_SEED_PHRASE = "extra_restore_seed_phrase"
        const val EXTRA_PDF_URI = "extra_pdf_uri"
        const val EXTRA_KEYSTORE_JSON = "extra_keystore_json"
        const val EXTRA_PRIVATE_KEY = "extra_private_key"
        const val EXTRA_ADDRESS = "extra_address"

        fun launchKeyStore(context: Context, keystoreJson: String? = null) {
            context.startActivity(Intent(context, KeyStoreRestoreActivity::class.java).apply {
                keystoreJson?.let { putExtra(EXTRA_KEYSTORE_JSON, it) }
            })
        }

        fun launchPrivateKey(context: Context, pdfUri: String? = null) {
            context.startActivity(Intent(context, KeyStoreRestoreActivity::class.java).apply {
                putExtra(EXTRA_RESTORE_PRIVATE_KEY, true)
                if (pdfUri != null) {
                    putExtra(EXTRA_PDF_URI, pdfUri)
                }
            })
        }

        fun launchPrivateKeyWithData(context: Context, privateKey: String, address: String? = null) {
            context.startActivity(Intent(context, KeyStoreRestoreActivity::class.java).apply {
                putExtra(EXTRA_RESTORE_PRIVATE_KEY, true)
                putExtra(EXTRA_PRIVATE_KEY, privateKey)
                address?.let { putExtra(EXTRA_ADDRESS, it) }
            })
        }

        fun launchSeedPhrase(context: Context) {
            context.startActivity(Intent(context, KeyStoreRestoreActivity::class.java).apply {
                putExtra(EXTRA_RESTORE_SEED_PHRASE, true)
            })
        }
    }
}
