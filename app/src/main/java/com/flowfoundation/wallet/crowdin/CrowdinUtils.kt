package com.flowfoundation.wallet.crowdin

import android.app.Application
import com.crowdin.platform.Crowdin
import com.crowdin.platform.CrowdinConfig
import com.crowdin.platform.data.remote.NetworkType


fun crowdinInitialize(application: Application) {
    Crowdin.init(application,
        CrowdinConfig.Builder()
            .withDistributionHash("d0024f6c98c8349bd86e629qzvm")
            .withNetworkType(NetworkType.ALL)
            .build())
}