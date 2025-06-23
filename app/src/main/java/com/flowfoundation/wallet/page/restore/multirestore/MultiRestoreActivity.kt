package com.flowfoundation.wallet.page.restore.multirestore

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityMultiRestoreBinding
import com.flowfoundation.wallet.databinding.DialogMultiBackupInfoBinding
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreOption
import com.flowfoundation.wallet.page.restore.multirestore.presenter.MultiRestorePresenter
import com.flowfoundation.wallet.page.restore.multirestore.viewmodel.MultiRestoreViewModel


class MultiRestoreActivity: BaseActivity() {

    private lateinit var binding: ActivityMultiRestoreBinding
    private lateinit var restorePresenter: MultiRestorePresenter
    private lateinit var restoreViewModel: MultiRestoreViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultiRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        restorePresenter = MultiRestorePresenter(this)
        restoreViewModel = ViewModelProvider(this)[MultiRestoreViewModel::class.java].apply {
            optionChangeLiveData.observe(this@MultiRestoreActivity) {
                restorePresenter.bind(it)
            }
            changeOption(RestoreOption.RESTORE_START, -1)
        }
        setupToolbar()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.multi_restore, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }
            R.id.action_info -> {
                showMultiBackupInfoDialog()
            }
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showMultiBackupInfoDialog() {
        val dialogBinding = DialogMultiBackupInfoBinding.inflate(layoutInflater)
        val dialog = Dialog(this)
        dialog.setContentView(dialogBinding.root)
        
        // Make dialog background transparent and set proper window attributes
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.let { params ->
            params.width = (resources.displayMetrics.widthPixels * 0.9).toInt() // 90% of screen width
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            dialog.window?.attributes = params
        }

        dialogBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, MultiRestoreActivity::class.java))
        }
    }
}