package io.outblock.lilico.manager.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import io.outblock.lilico.page.profile.subpage.claimdomain.checkMeowDomainClaimed
import io.outblock.lilico.page.profile.subpage.wallet.queryStorageInfo
import io.outblock.lilico.utils.logd
import io.outblock.lilico.utils.logw
import io.outblock.lilico.utils.safeRun

private const val TAG = "AppLifecycleObserver"

class AppLifecycleObserver : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        onAppToForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        onAppToBackground()
    }

    private fun onAppToForeground() {
        logd(TAG, "onAppToForeground")
        isForeground = true
        checkMeowDomainClaimed()
        queryStorageInfo()
        safeRun {
            CoreClient.Relay.connect { error: Core.Model.Error ->
                logw(TAG, "RelayClient connect error: $error")
            }
        }
    }

    private fun onAppToBackground() {
        isForeground = false
        logd(TAG, "onAppToBackground")
    }

    companion object {

        private var isForeground = false

        fun observe() {
            ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
        }

        fun isForeground() = isForeground
    }
}