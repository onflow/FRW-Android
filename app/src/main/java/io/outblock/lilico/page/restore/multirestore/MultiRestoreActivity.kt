package io.outblock.lilico.page.restore.multirestore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivityMultiRestoreBinding
import io.outblock.lilico.page.restore.multirestore.model.RestoreOption
import io.outblock.lilico.page.restore.multirestore.presenter.MultiRestorePresenter
import io.outblock.lilico.page.restore.multirestore.viewmodel.MultiRestoreViewModel


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

    override fun onBackPressed() {
        if (restoreViewModel.handleBackPressed()) {
            return
        }
        super.onBackPressed()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (restoreViewModel.handleBackPressed()) {
                    return true
                }
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