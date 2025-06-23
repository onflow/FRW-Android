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
import com.flowfoundation.wallet.page.restore.keystore.fragment.KeyStoreNoAccountDialog
import com.flowfoundation.wallet.page.restore.keystore.fragment.KeyStoreSelectAccountDialog
import com.flowfoundation.wallet.page.restore.keystore.fragment.PrivateKeyInfoFragment
import com.flowfoundation.wallet.page.restore.keystore.fragment.PrivateKeyStoreInfoFragment
import com.flowfoundation.wallet.page.restore.keystore.fragment.PrivateKeyStoreUsernameFragment
import com.flowfoundation.wallet.page.restore.keystore.fragment.SeedPhraseInfoFragment
import com.flowfoundation.wallet.page.restore.keystore.model.KeyStoreOption
import com.flowfoundation.wallet.page.restore.keystore.viewmodel.KeyStoreRestoreViewModel
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.FlowLoadingDialog
import com.google.android.material.transition.MaterialSharedAxis
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX


class KeyStoreRestoreActivity : BaseActivity() {

    private lateinit var binding: ActivityRestoreKeyStoreBinding
    private lateinit var restoreViewModel: KeyStoreRestoreViewModel
    private val loadingDialog by lazy { FlowLoadingDialog(this) }
    private var currentOption: KeyStoreOption? = null
    private val isKeyStore by lazy { intent.getBooleanExtra(EXTRA_IS_KEYSTORE, false) }
    private val isPrivateKey by lazy { intent.getBooleanExtra(EXTRA_IS_PRIVATE_KEY, false) }
    private val isSeedPhrase by lazy { intent.getBooleanExtra(EXTRA_IS_SEED_PHRASE, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRestoreKeyStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).color(android.graphics.Color.BLACK).light(false).applyStatusBar()
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
                        KeyStoreNoAccountDialog().show(supportFragmentManager, "")
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

    fun updateToolbarTitle(option: KeyStoreOption) {
        supportActionBar?.setTitle(option.titleResId)
    }

    @SuppressLint("CommitTransaction")
    private fun onOptionChange(option: KeyStoreOption) {
        val transition = createTransition(option)
        val fragment = when (option) {
            KeyStoreOption.INPUT_KEYSTORE_INFO -> PrivateKeyStoreInfoFragment()
            KeyStoreOption.INPUT_PRIVATE_KEY_INFO -> PrivateKeyInfoFragment()
            KeyStoreOption.INPUT_SEED_PHRASE_INFO -> SeedPhraseInfoFragment()
            KeyStoreOption.CREATE_USERNAME -> PrivateKeyStoreUsernameFragment()
        }
        fragment.enterTransition = transition
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        
        // Update toolbar title
        updateToolbarTitle(option)
        
        currentOption = option
    }

    private fun createTransition(
        option: KeyStoreOption
    ): Transition {
        if (currentOption == null) {
            return Fade().apply { duration = 50 }
        }
        val transition = MaterialSharedAxis(MaterialSharedAxis.X, true)

        transition.addTarget(currentOption!!.layoutId)
        transition.addTarget(option.layoutId)
        return transition
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }

            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        private const val EXTRA_IS_KEYSTORE = "EXTRA_IS_KEYSTORE"
        private const val EXTRA_IS_PRIVATE_KEY = "EXTRA_IS_PRIVATE_KEY"
        private const val EXTRA_IS_SEED_PHRASE = "EXTRA_IS_SEED_PHRASE"

        fun launch(context: Context) {
            context.startActivity(Intent(context, KeyStoreRestoreActivity::class.java))
        }

        fun launchKeyStore(context: Context) {
            context.startActivity(Intent(context, KeyStoreRestoreActivity::class.java).apply {
                putExtra(EXTRA_IS_KEYSTORE, true)
            })
        }

        fun launchPrivateKey(context: Context) {
            context.startActivity(Intent(context, KeyStoreRestoreActivity::class.java).apply {
                putExtra(EXTRA_IS_PRIVATE_KEY, true)
            })
        }

        fun launchSeedPhrase(context: Context) {
            context.startActivity(Intent(context, KeyStoreRestoreActivity::class.java).apply {
                putExtra(EXTRA_IS_SEED_PHRASE, true)
            })
        }
    }
}