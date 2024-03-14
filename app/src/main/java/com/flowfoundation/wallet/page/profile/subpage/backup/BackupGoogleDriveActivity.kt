package com.flowfoundation.wallet.page.profile.subpage.backup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityBackupManuallyBinding
import com.flowfoundation.wallet.page.walletcreate.fragments.cloudpwd.WalletCreateCloudPwdFragment

class BackupGoogleDriveActivity : BaseActivity() {

    private lateinit var binding: ActivityBackupManuallyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupManuallyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()

        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, WalletCreateCloudPwdFragment()).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        title = ""
        binding.toolbar.setTitle("")
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, BackupGoogleDriveActivity::class.java))
        }
    }
}