package com.flowfoundation.wallet.firebase.config

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.utils.logd


fun initFirebaseConfig() {
    val config = Firebase.remoteConfig
    val configSettings = remoteConfigSettings {
        minimumFetchIntervalInSeconds = 3600
    }
    config.setConfigSettingsAsync(configSettings)
    config.setDefaultsAsync(R.xml.remote_config_defaults).addOnCompleteListener {
        logd("initFirebaseConfig", "from local default")
        onConfigLoadFinish()
    }
    firebaseConfigFetch()
}

fun firebaseConfigFetch() {
    Firebase.remoteConfig.fetchAndActivate().addOnCompleteListener {
        onConfigLoadFinish()
    }
}

fun fetchLatestFirebaseConfig() {
    val configSettings = remoteConfigSettings {
        minimumFetchIntervalInSeconds = 0
    }
    Firebase.remoteConfig.apply {
        setConfigSettingsAsync(configSettings)
    }.fetchAndActivate().addOnCompleteListener {
        onConfigLoadFinish()
    }
}

private fun onConfigLoadFinish() {
    FlowCoinListManager.reload()
    AppConfig.sync()
}