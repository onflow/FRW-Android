package com.flowfoundation.wallet.page.main.presenter

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.journeyapps.barcodescanner.ScanOptions
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.LayoutMainDrawerLayoutBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.OnWalletDataUpdate
import com.flowfoundation.wallet.manager.account.WalletFetcher
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.manager.childaccount.ChildAccountList
import com.flowfoundation.wallet.manager.childaccount.ChildAccountUpdateListenerCallback
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.WalletListData
import com.flowfoundation.wallet.page.dialog.accounts.AccountSwitchDialog
import com.flowfoundation.wallet.page.main.MainActivityViewModel
import com.flowfoundation.wallet.page.main.model.MainDrawerLayoutModel
import com.flowfoundation.wallet.page.main.refreshWalletList
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftCache
import com.flowfoundation.wallet.page.scan.dispatchScanResult
import com.flowfoundation.wallet.utils.ScreenUtils
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.launch
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.registerBarcodeLauncher
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.ProgressDialog
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog
import org.joda.time.format.ISODateTimeFormat

class DrawerLayoutPresenter(
    private val drawer: DrawerLayout,
    private val binding: LayoutMainDrawerLayoutBinding,
) : BasePresenter<MainDrawerLayoutModel>, ChildAccountUpdateListenerCallback, OnWalletDataUpdate {

    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>

    private val activity by lazy { findActivity(drawer) as FragmentActivity }
    private val progressDialog by lazy { ProgressDialog(activity) }

    init {
        drawer.addDrawerListener(DrawerListener())

        with(binding.root.layoutParams) {
            width = (ScreenUtils.getScreenWidth() * 0.8f).toInt()
            binding.root.layoutParams = this
        }

        with(binding) {
            scanItem.setOnClickListener { launchClick { barcodeLauncher.launch() } }
            accountSwitchButton.setOnClickListener { AccountSwitchDialog.show(activity.supportFragmentManager) }
        }
        bindData()
        bindAccountData()
        binding.refreshWalletList()
        barcodeLauncher = activity.registerBarcodeLauncher { result -> dispatchScanResult(activity, result.orEmpty()) }

        ChildAccountList.addAccountUpdateListener(this)
        WalletFetcher.addListener(this)
    }

    private fun bindAccountData() {
        binding.llAccountLayout.removeAllViews()
        val list = AccountManager.list().filter { it.isActive.not() }
        list.take(2).forEach { account ->
            binding.llAccountLayout.addView(ImageFilterView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    44.dp2px().toInt(),
                    28.dp2px().toInt()
                )
                setPadding(8.dp2px().toInt(), 0, 8.dp2px().toInt(), 0)
                setOnClickListener {
                    if (isTestnet()) {
                        SwitchNetworkDialog(activity, DialogType.SWITCH).show()
                    } else {
                        progressDialog.show()
                        AccountManager.switch(account) {
                            uiScope {
                                progressDialog.dismiss()
                            }
                        }
                    }
                }
                loadAvatar(account.userInfo.avatar)
            })
        }
    }

    override fun bind(model: MainDrawerLayoutModel) {
        model.refreshData?.let { bindData() }
        model.openDrawer?.let { drawer.open() }
    }

    private fun bindData() {
        ioScope {
            val address = WalletManager.selectedWalletAddress()
            drawer.setDrawerLockMode(if (address.isBlank()) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED)

            val userInfo = AccountManager.userInfo() ?: return@ioScope
            val nftCount = NftCache(address).grid().read()?.count ?: 0
            val createTime = ISODateTimeFormat.dateTimeParser().parseDateTime(userInfo.created).toString("yyyy")
            uiScope {
                with(binding) {
                    avatarView.loadAvatar(userInfo.avatar)
                    nickNameView.text = userInfo.nickname
                    descView.text = activity.getString(R.string.drawer_desc, createTime, nftCount)
                }
            }
        }
    }

    private fun launchClick(unit: () -> Unit) {
        unit.invoke()
        drawer.close()
    }

    private inner class DrawerListener : DrawerLayout.SimpleDrawerListener() {
        override fun onDrawerOpened(drawerView: View) {
            super.onDrawerOpened(drawerView)
            bindData()
        }
    }

    override fun onChildAccountUpdate(parentAddress: String, accounts: List<ChildAccount>) {
        binding.refreshWalletList()
    }

    override fun onWalletDataUpdate(wallet: WalletListData) {
        binding.refreshWalletList()
    }
}

fun openDrawerLayout(context: Context) {
    val activity = context as? FragmentActivity ?: return
    val viewModel = ViewModelProvider(activity)[MainActivityViewModel::class.java]
    viewModel.openDrawerLayout()
}