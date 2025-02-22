package com.flowfoundation.wallet.page.profile.subpage.developer.presenter

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.cache.UserPrefixCacheManager
import com.flowfoundation.wallet.databinding.ActivityDeveloperModeSettingBinding
import com.flowfoundation.wallet.firebase.config.fetchLatestFirebaseConfig
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.AccountWalletManager
import com.flowfoundation.wallet.manager.app.doNetworkChangeTask
import com.flowfoundation.wallet.manager.app.isDeveloperMode
import com.flowfoundation.wallet.manager.app.isMainnet
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.manager.app.refreshChainNetworkSync
import com.flowfoundation.wallet.manager.cadence.CadenceApiManager
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.key.HDWalletCryptoProvider
import com.flowfoundation.wallet.page.profile.subpage.developer.DeveloperModeViewModel
import com.flowfoundation.wallet.page.profile.subpage.developer.LocalAccountKeyActivity
import com.flowfoundation.wallet.page.profile.subpage.developer.model.DeveloperPageModel
import com.flowfoundation.wallet.utils.NETWORK_MAINNET
import com.flowfoundation.wallet.utils.NETWORK_TESTNET
import com.flowfoundation.wallet.utils.debug.DebugLogManager
import com.flowfoundation.wallet.utils.debug.DebugManager
import com.flowfoundation.wallet.utils.debug.fragments.debugViewer.DebugViewerDataSource
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.setDeveloperModeEnable
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.updateChainNetworkPreference
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.widgets.ProgressDialog
import com.google.gson.Gson
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

class DeveloperModePresenter(
    private val activity: FragmentActivity,
    private val binding: ActivityDeveloperModeSettingBinding,
) : BasePresenter<DeveloperPageModel> {

    private val progressDialog by lazy { ProgressDialog(activity) }

    private val viewModel by lazy { ViewModelProvider(activity)[DeveloperModeViewModel::class.java] }

    private var clickCount = 0
    private var maxClick = 6
    private var clickThresholdTime = 2000L
    private var lastClickTime = 0L
    private var showLocalAccountKeys = false

    init {
        uiScope {
            with(binding) {
                val isDeveloperModeEnable = isDeveloperMode()
                developerModePreference.setChecked(isDeveloperModeEnable)
                setDevelopContentVisible(isDeveloperModeEnable)
                mainnetPreference.setChecked(isMainnet())
                testnetPreference.setChecked(isTestnet())

                mainnetPreference.setCheckboxColor(R.color.colorSecondary.res2color())
                testnetPreference.setCheckboxColor(R.color.testnet.res2color())

                mainnetPreference.setOnCheckedChangeListener {
                    testnetPreference.setChecked(!it)
                    changeNetwork(if (it) NETWORK_MAINNET else NETWORK_TESTNET)
                }

                testnetPreference.setOnCheckedChangeListener {
                    mainnetPreference.setChecked(!it)
                    changeNetwork(if (it) NETWORK_TESTNET else NETWORK_MAINNET)
                }

                developerModePreference.setOnCheckedChangeListener {
                    setDevelopContentVisible(it)
                    setDeveloperModeEnable(it)
                    if (!it) {
                        changeNetwork(NETWORK_MAINNET)
                    }
                }

                DebugManager.setFragmentManger(activity.supportFragmentManager, R.id.debug_container)

                DebugManager.initialize(DebugLogManager.tweaks)
                debugViewPreference.setOnCheckedChangeListener {
                    DebugManager.toggleDebugViewer()
                }
                tvExportLog.setOnClickListener {
                    DebugViewerDataSource.exportDebugMessagesAndShare(activity)
//                    exportAccountInfo()
                }
                tvCadenceScriptVersion.text = activity.getString(R.string.cadence_script_version, CadenceApiManager.getCadenceScriptVersion())
                cvAccountKey.setOnClickListener {
                    LocalAccountKeyActivity.launch(activity)
                }
                cvReloadConfig.setOnClickListener {
                    fetchLatestFirebaseConfig()
                }
                tvCadenceScriptVersion.setOnClickListener {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime <= clickThresholdTime) {
                        ++clickCount
                    } else {
                        clickCount = 1
                    }

                    lastClickTime = currentTime

                    if (clickCount == maxClick) {
                        clickCount = 0
                        showLocalAccountKeys = true
                        cvAccountKey.visible()
                    }
                }
            }
        }
    }

    private fun setDevelopContentVisible(visible: Boolean) {
        binding.group2.setVisible(visible)
        binding.cvDebug.setVisible(visible)
        binding.cvAccountKey.setVisible(visible && showLocalAccountKeys)
        binding.cvReloadConfig.setVisible(visible)
    }

    override fun bind(model: DeveloperPageModel) {
        model.progressDialogVisible?.let { if (it) progressDialog.show() else progressDialog.dismiss() }
        model.result?.let {
            if (!it) {
                toast(msgRes = R.string.switch_network_failed)
                with(binding) {
                    mainnetPreference.setChecked(isTestnet())
                    testnetPreference.setChecked(isMainnet())
                }
            }
        }
    }

    private fun exportAccountInfo() {
        val accountList = AccountManager.list()
        val stringBuilder = StringBuilder()
        accountList.forEach {
            stringBuilder.append("Username: ${it.userInfo.username}\n")
            stringBuilder.append("Info: ${Gson().toJson(it)}\n")
            val provider = if (it.isActive) {
                CryptoProviderManager.getCurrentCryptoProvider()
            } else {
                CryptoProviderManager.generateAccountCryptoProvider(it)
            }
            stringBuilder.append("CryptoProvider: ${provider?.javaClass?.simpleName}\n")
            stringBuilder.append("PublicKey: ${provider?.getPublicKey()}\n")
            if (provider is HDWalletCryptoProvider) {
                stringBuilder.append("HDWallet: ${Wallet.store().wallet()}\n")
            }
            stringBuilder.append("---------------------\n")
        }
        stringBuilder.append("LocalWallet: ${AccountWalletManager.getUIDPublicKeyMap()}\n")
        stringBuilder.append("LocalPrefix: ${UserPrefixCacheManager.read()}\n")

        val fileName = "debug_account_info.txt"
        val file = File(activity.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos).use { writer ->
                writer.write(stringBuilder.toString())
            }
        }

        val fileUri: Uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.provider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        activity.startActivity(Intent.createChooser(shareIntent, "Share Debug Account Info"))
    }

    private fun changeNetwork(network: Int) {
        updateChainNetworkPreference(network) {
            ioScope {
                delay(200)
                refreshChainNetworkSync()
                doNetworkChangeTask()
                uiScope {
                    delay(200)
                    viewModel.changeNetwork()
                    binding.mainnetPreference.setChecked(isMainnet())
                    binding.testnetPreference.setChecked(isTestnet())
                }
            }
        }
    }
}