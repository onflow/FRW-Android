package com.flowfoundation.wallet.page.profile.subpage.avatar

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.crowdin.platform.util.inflateWithCrowdin
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityViewAvatarBinding
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.profile.subpage.avatar.edit.EditAvatarActivity
import com.flowfoundation.wallet.utils.loadAvatar

class ViewAvatarActivity : BaseActivity() {
    private val userInfo by lazy { intent.getParcelableExtra<UserInfoData>(EXTRA_USER_INFO)!! }

    private lateinit var binding: ActivityViewAvatarBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewAvatarBinding.inflate(layoutInflater)
        UltimateBarX.with(this).fitWindow(false).light(false).applyStatusBar()
        setContentView(binding.root)
        setupToolbar()

        binding.imageView.loadAvatar(userInfo.avatar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflateWithCrowdin(R.menu.edit_avatar, menu, resources)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.action_edit -> {
                EditAvatarActivity.launch(this, userInfo)
                finish()
            }
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.navigationIcon?.mutate()?.setTint(Color.WHITE)
        binding.toolbar.addStatusBarTopPadding()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    companion object {
        private const val EXTRA_USER_INFO = "EXTRA_USER_INFO"

        fun launch(context: Context, userInfo: UserInfoData) {
            context.startActivity(Intent(context, ViewAvatarActivity::class.java).apply {
                putExtra(EXTRA_USER_INFO, userInfo)
            })
        }
    }
}