package com.flowfoundation.wallet.page.profile.subpage.accountsetting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityAccountSettingBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.UpdateProfilePreferenceRequest
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.page.profile.subpage.avatar.ViewAvatarActivity
import com.flowfoundation.wallet.page.profile.subpage.nickname.EditNicknameActivity
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.ProgressDialog

class AccountSettingActivity : BaseActivity() {

    private lateinit var binding: ActivityAccountSettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).colorRes(R.color.background).light(!isNightMode(this)).applyStatusBar()
        binding.root.addStatusBarTopPadding()
        setupToolbar()
        setup(intent.getParcelableExtra(EXTRA_USER_INFO)!!)
    }

    override fun onRestart() {
        super.onRestart()
        ioScope {
            AccountManager.userInfo()?.let { uiScope { setup(it) } }
        }
    }

    private fun setup(userInfo: UserInfoData) {
        with(binding) {
            avatarPreference.setImageUrl(userInfo.avatar)
            nicknamePreference.setDesc(userInfo.nickname)
            visiblePreference.updateState(userInfo.isPrivate == 2)
            visiblePreference.icons(R.drawable.ic_user_visible, R.drawable.ic_user_invisible)

            avatarPreference.setOnClickListener { ViewAvatarActivity.launch(this@AccountSettingActivity, userInfo) }
            nicknamePreference.setOnClickListener { EditNicknameActivity.launch(this@AccountSettingActivity) }
            visiblePreference.setOnStateChangeListener { isVisible -> updateVisible(isVisible) }
        }
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
    }

    private fun updateVisible(toVisible: Boolean) {
        val progressDialog = ProgressDialog(this).apply { show() }
        ioScope {
            try {
                val response = retrofit().create(ApiService::class.java)
                    .updateProfilePreference(UpdateProfilePreferenceRequest())
                if (response.status == 200) {
                    AccountManager.userInfo()?.copy(isPrivate = if (toVisible) 2 else 1)?.let { AccountManager.updateUserInfo(it) }

                    val service = retrofit().create(ApiService::class.java)
                    val data = service.userInfo().data
                    AccountManager.updateUserInfo(data)
                    uiScope { binding.visiblePreference.updateState(toVisible) }
                }
            } catch (e: Exception) {
                loge(e)
                toast(msgRes = R.string.network_error)
            }

            uiScope { progressDialog.dismiss() }
        }
    }


    companion object {
        private const val EXTRA_USER_INFO = "extra_user_info"

        fun launch(context: Context, userInfo: UserInfoData) {
            context.startActivity(Intent(context, AccountSettingActivity::class.java).apply {
                putExtra(EXTRA_USER_INFO, userInfo)
            })
        }
    }
}