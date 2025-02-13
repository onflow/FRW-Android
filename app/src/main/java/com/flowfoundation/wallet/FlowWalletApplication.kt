package com.flowfoundation.wallet

import android.app.Application
import com.flowfoundation.wallet.manager.LaunchManager
import com.flowfoundation.wallet.utils.Env

class FlowWalletApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Env.init(this)
        LaunchManager.init(this)
    }
}