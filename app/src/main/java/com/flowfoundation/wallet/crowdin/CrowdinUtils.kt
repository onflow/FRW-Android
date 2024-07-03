package com.flowfoundation.wallet.crowdin

import android.app.Application
import com.crowdin.platform.Crowdin
import com.crowdin.platform.CrowdinConfig
import com.crowdin.platform.data.remote.NetworkType
import com.flowfoundation.wallet.manager.env.EnvKey


fun crowdinInitialize(application: Application) {
    Crowdin.init(application,
        CrowdinConfig.Builder()
            .withDistributionHash(EnvKey.get("CROWDIN_DISTRIBUTION"))
            .withNetworkType(NetworkType.ALL)
            .build())
}