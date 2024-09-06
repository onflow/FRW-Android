package com.flowfoundation.wallet.page.staking.guide

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.page.staking.providers.StakingProviderActivity
import com.flowfoundation.wallet.utils.setStakingGuideDisplayed

class StakeGuideActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stake_guide)
        findViewById<View>(R.id.stake_button).setOnClickListener {
            if (StakingManager.hasBeenSetup()) {
                StakingProviderActivity.launch(this)
            } else {
                StakingSetupDialog.show(this)
            }
        }
        setupToolbar()
        setStakingGuideDisplayed()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, StakeGuideActivity::class.java))
        }
    }
}