package com.flowfoundation.wallet.page.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.databinding.ActivityMainBinding
import com.flowfoundation.wallet.firebase.firebaseInformationCheck
import com.flowfoundation.wallet.page.dialog.common.RootDetectedDialog
import com.flowfoundation.wallet.page.main.model.MainContentModel
import com.flowfoundation.wallet.page.main.model.MainDrawerLayoutModel
import com.flowfoundation.wallet.page.main.presenter.DrawerLayoutPresenter
import com.flowfoundation.wallet.page.main.presenter.MainContentPresenter
import com.flowfoundation.wallet.page.others.NotificationPermissionActivity
import com.flowfoundation.wallet.page.window.WindowFrame
import com.flowfoundation.wallet.utils.isNewVersion
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.isNotificationPermissionChecked
import com.flowfoundation.wallet.utils.isNotificationPermissionGrand
import com.flowfoundation.wallet.utils.isRegistered
import com.flowfoundation.wallet.utils.uiScope

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

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navigationView.updatePadding(bottom = systemBarsInsets.bottom)
            windowInsets
        }
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
            contentPresenter.checkAndShowContent()
        }
        WindowFrame.attach(this)

        if (!isNotificationPermissionChecked() && !isNotificationPermissionGrand(this)) {
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
                contentPresenter.checkAndShowContent()
            }
            drawerLayoutPresenter.bind(MainDrawerLayoutModel(refreshData = true))
        }
    }

    override fun onResume() {
        RootDetectedDialog.show(supportFragmentManager)
        super.onResume()
    }

    override fun onDestroy() {
        if (INSTANCE == this) {
            INSTANCE = null
        }
        WindowFrame.release()
        super.onDestroy()
    }

    companion object {

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