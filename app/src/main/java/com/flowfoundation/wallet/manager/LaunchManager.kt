package com.flowfoundation.wallet.manager

import android.app.Application
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import com.flowfoundation.wallet.crowdin.crowdinInitialize
import com.flowfoundation.wallet.firebase.config.initFirebaseConfig
import com.flowfoundation.wallet.firebase.firebaseInitialize
import com.flowfoundation.wallet.instabug.instabugInitialize
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.BalanceManager
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.manager.app.AppLifecycleObserver
import com.flowfoundation.wallet.manager.app.PageLifecycleObserver
import com.flowfoundation.wallet.manager.app.refreshChainNetwork
import com.flowfoundation.wallet.manager.cadence.CadenceApiManager
import com.flowfoundation.wallet.manager.coin.CoinRateManager
import com.flowfoundation.wallet.manager.coin.CustomTokenManager
import com.flowfoundation.wallet.manager.coin.TokenStateManager
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.flowjvm.FlowApi
import com.flowfoundation.wallet.manager.nft.NftCollectionStateManager
import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.manager.staking.StakingManager
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.service.MessagingService
import com.flowfoundation.wallet.utils.getThemeMode
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.safeRun
import com.flowfoundation.wallet.utils.startServiceSafe
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.restoreMnemonicV0

object LaunchManager {

    fun init(application: Application) {
        application.startServiceSafe(Intent(application, MessagingService::class.java))
        PageLifecycleObserver.init(application)
        safeRun { System.loadLibrary("TrustWalletCore") }
        ioScope {
            safeRun { AccountManager.init() }
        }
        refreshChainNetwork {
            safeRun { MixpanelManager.init(application) }
            safeRun { WalletConnect.init(application) }
            safeRun { initFirebaseConfig() }
            safeRun { FlowApi.refreshConfig() }
            safeRun { FlowCadenceApi.refreshConfig() }
            safeRun { asyncInit() }
            safeRun { firebaseInitialize(application) }
            safeRun { instabugInitialize(application) }
            safeRun { crowdinInitialize(application) }
            safeRun { setNightMode() }
            safeRun { runWorker() }
            safeRun { readCache() }
            safeRun { runCompatibleScript() }
        }
        AppLifecycleObserver.observe()
    }

    private fun asyncInit() {
        ioScope {
            DeviceInfoManager.updateDeviceInfo()
        }
    }

    private fun readCache() {
        safeRun { WalletManager.init() }
        safeRun { CustomTokenManager.init() }
        safeRun { NftCollectionConfig.sync() }
        safeRun { BalanceManager.reload() }
        safeRun { TransactionStateManager.reload() }
        safeRun { TokenStateManager.reload() }
        safeRun { NftCollectionStateManager.reload() }
        safeRun { CoinRateManager.init() }
        safeRun { CurrencyManager.init() }
        safeRun { StakingManager.init() }
    }

    private fun setNightMode() {
        uiScope { AppCompatDelegate.setDefaultNightMode(getThemeMode()) }
    }

    private fun runWorker() {
        CadenceApiManager.init()
        MixpanelManager.identifyUserProfile()
    }

    /**
     * Run compatible script if necessary
     * Sometimes the version upgrade and modification configuration have to be compatible with the old version
     */
    private fun runCompatibleScript() {
        restoreMnemonicV0()
    }
}