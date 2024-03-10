package io.outblock.lilico.page.main.presenter

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.journeyapps.barcodescanner.ScanOptions
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.databinding.LayoutMainDrawerLayoutBinding
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.manager.account.OnWalletDataUpdate
import io.outblock.lilico.manager.account.WalletFetcher
import io.outblock.lilico.manager.app.isTestnet
import io.outblock.lilico.manager.childaccount.ChildAccount
import io.outblock.lilico.manager.childaccount.ChildAccountList
import io.outblock.lilico.manager.childaccount.ChildAccountUpdateListenerCallback
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.model.WalletListData
import io.outblock.lilico.page.dialog.accounts.AccountSwitchDialog
import io.outblock.lilico.page.main.MainActivityViewModel
import io.outblock.lilico.page.main.model.MainDrawerLayoutModel
import io.outblock.lilico.page.main.refreshWalletList
import io.outblock.lilico.page.nft.nftlist.utils.NftCache
import io.outblock.lilico.page.scan.dispatchScanResult
import io.outblock.lilico.utils.ScreenUtils
import io.outblock.lilico.utils.extensions.dp2px
import io.outblock.lilico.utils.findActivity
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.launch
import io.outblock.lilico.utils.loadAvatar
import io.outblock.lilico.utils.registerBarcodeLauncher
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.widgets.DialogType
import io.outblock.lilico.widgets.ProgressDialog
import io.outblock.lilico.widgets.SwitchNetworkDialog
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