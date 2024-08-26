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
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupCompletedFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupGoogleDriveFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupGoogleDriveWithPinFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupPinCodeFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupRecoveryPhraseFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupStartFragment
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupGoogleDriveOption
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOption
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOptionModel
import com.flowfoundation.wallet.page.restore.keystore.fragment.KeyStoreSelectAccountDialog
import com.flowfoundation.wallet.page.restore.keystore.fragment.PrivateKeyStoreInfoFragment
import com.flowfoundation.wallet.page.restore.keystore.fragment.PrivateKeyStoreUsernameFragment
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
                    // todo
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
            changeOption(KeyStoreOption.INPUT_INFO)
        }
    }

    @SuppressLint("CommitTransaction")
    private fun onOptionChange(option: KeyStoreOption) {
        val transition = createTransition(currentOption, option)
        val fragment = when (option) {
            KeyStoreOption.INPUT_INFO -> PrivateKeyStoreInfoFragment()
            KeyStoreOption.CREATE_USERNAME -> PrivateKeyStoreUsernameFragment()
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
        fun launch(context: Context) {
            context.startActivity(Intent(context, KeyStoreRestoreActivity::class.java))
        }
    }
}