package com.flowfoundation.wallet.page.restore.multirestore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityMultiRestoreBinding
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
            context.startActivity(Intent(context, MultiRestoreActivity::class.java))
        }
    }
}