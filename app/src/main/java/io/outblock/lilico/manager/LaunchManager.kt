package io.outblock.lilico.manager

import android.app.Application
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import io.outblock.lilico.firebase.config.initFirebaseConfig
import io.outblock.lilico.firebase.firebaseInitialize
import io.outblock.lilico.instabug.instabugInitialize
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.manager.account.BalanceManager
import io.outblock.lilico.manager.account.DeviceInfoManager
import io.outblock.lilico.manager.app.AppLifecycleObserver
import io.outblock.lilico.manager.app.PageLifecycleObserver
import io.outblock.lilico.manager.app.refreshChainNetwork
import io.outblock.lilico.manager.coin.CoinRateManager
import io.outblock.lilico.manager.coin.TokenStateManager
import io.outblock.lilico.manager.config.NftCollectionConfig
import io.outblock.lilico.manager.env.EnvKey
import io.outblock.lilico.manager.flowjvm.FlowApi
import io.outblock.lilico.manager.nft.NftCollectionStateManager
import io.outblock.lilico.manager.price.CurrencyManager
import io.outblock.lilico.manager.staking.StakingManager
import io.outblock.lilico.manager.transaction.TransactionStateManager
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.manager.walletconnect.WalletConnect
import io.outblock.lilico.service.MessagingService
import io.outblock.lilico.utils.getThemeMode
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.safeRun
import io.outblock.lilico.utils.startServiceSafe
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.wallet.restoreMnemonicV0

object LaunchManager {

    fun init(application: Application) {
        application.startServiceSafe(Intent(application, MessagingService::class.java))
        PageLifecycleObserver.init(application)
        safeRun { System.loadLibrary("TrustWalletCore") }
        ioScope {
            safeRun { EnvKey.init() }
            safeRun { AccountManager.init() }
        }
        refreshChainNetwork {
            safeRun { WalletConnect.init(application) }
            safeRun { initFirebaseConfig() }
            safeRun { FlowApi.refreshConfig() }
            safeRun { asyncInit() }
            safeRun { firebaseInitialize(application) }
            safeRun { instabugInitialize(application) }
            safeRun { setNightMode() }
            safeRun { runWorker(application) }
            safeRun { readCache(application) }
            safeRun { runCompatibleScript() }
        }
        AppLifecycleObserver.observe()
    }

    private fun asyncInit() {
        ioScope {
            DeviceInfoManager.updateDeviceInfo()
        }
    }

    private fun readCache(application: Application) {
        safeRun { WalletManager.init() }
        safeRun { NftCollectionConfig.sync() }
        safeRun { BalanceManager.reload() }
        safeRun { TransactionStateManager.reload() }
        safeRun { TokenStateManager.reload() }
        safeRun { NftCollectionStateManager.reload() }
        safeRun { CoinRateManager.init() }
        safeRun { CurrencyManager.init() }
        safeRun { StakingManager.init() }
//        Translized.init(application, EnvKey.get("TRANSLIZED_PROJECT_ID"), EnvKey.get("TRANSLIZED_TOKEN"))
    }

    private fun setNightMode() {
        uiScope { AppCompatDelegate.setDefaultNightMode(getThemeMode()) }
    }

    private fun runWorker(application: Application) {
    }

    /**
     * Run compatible script if necessary
     * Sometimes the version upgrade and modification configuration have to be compatible with the old version
     */
    private fun runCompatibleScript() {
        restoreMnemonicV0()
    }
}