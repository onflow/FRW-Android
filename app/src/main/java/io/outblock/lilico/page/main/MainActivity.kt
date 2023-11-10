package io.outblock.lilico.page.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import io.outblock.lilico.base.activity.BaseActivity
import io.outblock.lilico.databinding.ActivityMainBinding
import io.outblock.lilico.firebase.firebaseInformationCheck
import io.outblock.lilico.manager.account.AccountWalletManager
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.page.dialog.common.RootDetectedDialog
import io.outblock.lilico.page.guide.GuideActivity
import io.outblock.lilico.page.main.model.MainContentModel
import io.outblock.lilico.page.main.model.MainDrawerLayoutModel
import io.outblock.lilico.page.main.presenter.DrawerLayoutPresenter
import io.outblock.lilico.page.main.presenter.MainContentPresenter
import io.outblock.lilico.page.others.NotificationPermissionActivity
import io.outblock.lilico.page.window.WindowFrame
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.isGuidePageShown
import io.outblock.lilico.utils.isNewVersion
import io.outblock.lilico.utils.isNightMode
import io.outblock.lilico.utils.isNotificationPermissionChecked
import io.outblock.lilico.utils.isNotificationPermissionGrand
import io.outblock.lilico.utils.isRegistered
import io.outblock.lilico.utils.logd
import io.outblock.lilico.utils.uiScope


class MainActivity : BaseActivity() {

    private lateinit var contentPresenter: MainContentPresenter
    private lateinit var drawerLayoutPresenter: DrawerLayoutPresenter

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainActivityViewModel

    private var isRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        INSTANCE = this
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        UltimateBarX.with(this).fitWindow(false).light(!isNightMode(this)).applyStatusBar()
        UltimateBarX.with(this).fitWindow(true).light(!isNightMode(this)).applyNavigationBar()

        contentPresenter = MainContentPresenter(this, binding)
        drawerLayoutPresenter = DrawerLayoutPresenter(binding.drawerLayout, binding.drawerLayoutContent)
        viewModel = ViewModelProvider(this)[MainActivityViewModel::class.java].apply {
            changeTabLiveData.observe(this@MainActivity) { contentPresenter.bind(MainContentModel(onChangeTab = it)) }
            openDrawerLayoutLiveData.observe(this@MainActivity) { drawerLayoutPresenter.bind(MainDrawerLayoutModel(openDrawer = it)) }
        }
        uiScope {
            isRegistered = isRegistered()
            if (isNewVersion()) {
                firebaseInformationCheck()
            }
        }
        WindowFrame.attach(this)

        if (!isGuidePageShown()) {
            GuideActivity.launch(this)
        } else if (!isNotificationPermissionChecked() && !isNotificationPermissionGrand(this)) {
            NotificationPermissionActivity.launch(this)
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onRestart() {
        super.onRestart()
        uiScope {
            if (isRegistered != isRegistered()) {
                viewModel.walletRegisterSuccessLiveData.value = isRegistered()
            }
            drawerLayoutPresenter.bind(MainDrawerLayoutModel(refreshData = true))
        }
    }

    override fun onResume() {
        RootDetectedDialog.show(supportFragmentManager)
        super.onResume()
    }

    override fun onDestroy() {
        INSTANCE = null
        WindowFrame.release()
        super.onDestroy()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        private var INSTANCE: MainActivity? = null
        fun launch(context: Context) {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            })
        }

        fun relaunch(context: Context, clearTop: Boolean = false) {
            if (clearTop) {
                launch(context)
            }
            val instance = INSTANCE ?: (if (getCurrentActivity() is MainActivity) getCurrentActivity() else null)
            instance?.finish()
            instance?.overridePendingTransition(0, 0)
            launch(context)
            (context as? Activity)?.overridePendingTransition(0, 0)
        }

        fun getInstance() = INSTANCE
    }
}