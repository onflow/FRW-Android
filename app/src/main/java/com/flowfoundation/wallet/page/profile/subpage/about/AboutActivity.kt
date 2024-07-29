package com.flowfoundation.wallet.page.profile.subpage.about

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.BuildConfig
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityAboutBinding
import com.flowfoundation.wallet.utils.extensions.openInSystemBrowser
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.sendEmail

class AboutActivity : BaseActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(true).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyNavigationBar()

        with(binding) {
            versionView.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
            discordButton.setOnClickListener { "https://discord.com/invite/J6fFnh2xx6".openInSystemBrowser(this@AboutActivity, ignoreInAppBrowser = true) }
            twitterButton.setOnClickListener { "https://twitter.com/flow_blockchain".openInSystemBrowser(this@AboutActivity, ignoreInAppBrowser = true) }
            emailButton.setOnClickListener { sendEmail(this@AboutActivity, email = "hi@lilico.app") }
            madeByView.movementMethod = LinkMovementMethod.getInstance()
        }
        setupToolbar()
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
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, AboutActivity::class.java))
        }
    }
}