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
import com.flowfoundation.wallet.page.component.deeplinking.PendingActionHelper
import com.flowfoundation.wallet.page.component.deeplinking.executePendingDeepLink
import com.flowfoundation.wallet.page.dialog.common.RootDetectedDialog
import com.flowfoundation.wallet.page.main.model.MainContentModel
import com.flowfoundation.wallet.page.main.model.MainDrawerLayoutModel
import com.flowfoundation.wallet.page.main.presenter.DrawerLayoutPresenter
import com.flowfoundation.wallet.page.main.presenter.MainContentPresenter
import com.flowfoundation.wallet.page.others.NotificationPermissionActivity
import com.flowfoundation.wallet.page.window.WindowFrame
import com.flowfoundation.wallet.utils.debug.fragments.debugViewer.DebugViewerDataSource
import com.flowfoundation.wallet.utils.isNewVersion
import com.flowfoundation.wallet.utils.isNightMode
import com.flowfoundation.wallet.utils.isNotificationPermissionChecked
import com.flowfoundation.wallet.utils.isNotificationPermissionGrand
import com.flowfoundation.wallet.utils.isRegistered
import com.flowfoundation.wallet.utils.uiScope
import com.instabug.bug.BugReporting
import com.instabug.library.Instabug

class MainActivity : BaseActivity() {

    private lateinit var contentPresenter: MainContentPresenter
    private lateinit var drawerLayoutPresenter: DrawerLayoutPresenter

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainActivityViewModel

    private var isRegistered = false
    private val targetTabIndex by lazy { intent.getIntExtra(EXTRA_TARGET_TAB, -1) }

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
            
            // Navigate to target tab if specified
            if (targetTabIndex >= 0) {
                val targetTab = HomeTab.values().find { it.index == targetTabIndex }
                targetTab?.let { viewModel.changeTab(it) }
            }
        }
        WindowFrame.attach(this)

        if (!isNotificationPermissionChecked() && !isNotificationPermissionGrand(this)) {
            NotificationPermissionActivity.launch(this)
        }
        configurationInstabugBugReport()
    }

    private fun configurationInstabugBugReport() {
        BugReporting.setOnInvokeCallback {
            DebugViewerDataSource.generateDebugZipFile(this)?.let {
                Instabug.addFileAttachment(it, "log.zip")
            }
            BugReporting.setOnDismissCallback { _, _ ->
                Instabug.clearFileAttachment()
                BugReporting.setOnDismissCallback(null)
            }
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
        checkPendingAction()
    }

    private fun checkPendingAction() {
        if (PendingActionHelper.hasPendingDeepLink(this)) {
            val pendingDeeplink = PendingActionHelper.getPendingDeepLink(this)
            PendingActionHelper.clearPendingDeepLink(this)
            if (pendingDeeplink != null) {
                uiScope {
                    executePendingDeepLink(pendingDeeplink)
                }
            }
        }
    }

    override fun onDestroy() {
        if (INSTANCE == this) {
            INSTANCE = null
        }
        BugReporting.setOnInvokeCallback(null)
        WindowFrame.release()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TARGET_TAB = "extra_target_tab"

        private var INSTANCE: MainActivity? = null
        fun launch(context: Context, targetTab: HomeTab? = null) {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                targetTab?.let { putExtra(EXTRA_TARGET_TAB, it.index) }
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