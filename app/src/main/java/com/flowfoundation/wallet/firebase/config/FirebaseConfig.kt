package com.flowfoundation.wallet.firebase.config

import android.content.res.XmlResourceParser
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.firebase.FirebaseFlags
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import org.xmlpull.v1.XmlPullParser


fun initFirebaseConfig() {
    if (!FirebaseFlags.enabled) {
        logd("initFirebaseConfig", "Firebase disabled: using bundled remote_config_defaults.xml")
        onConfigLoadFinish()
        return
    }
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
    if (!FirebaseFlags.enabled) {
        onConfigLoadFinish()
        return
    }
    Firebase.remoteConfig.fetchAndActivate().addOnCompleteListener {
        onConfigLoadFinish()
    }
}

fun fetchLatestFirebaseConfig() {
    if (!FirebaseFlags.enabled) {
        onConfigLoadFinish()
        return
    }
    val configSettings = remoteConfigSettings {
        minimumFetchIntervalInSeconds = 0
    }
    Firebase.remoteConfig.apply {
        setConfigSettingsAsync(configSettings)
    }.fetchAndActivate().addOnCompleteListener {
        onConfigLoadFinish()
    }
}

/**
 * Remote Config value for [key]. With Firebase off, reads the bundled defaults
 * (res/xml/remote_config_defaults.xml). Missing keys return "", like FirebaseRemoteConfig.getString.
 */
fun remoteConfigString(key: String): String {
    if (FirebaseFlags.enabled) {
        return runCatching { Firebase.remoteConfig.getString(key) }.getOrDefault(bundledDefaults()[key].orEmpty())
    }
    return bundledDefaults()[key].orEmpty()
}

@Volatile
private var defaultsCache: Map<String, String>? = null

private fun bundledDefaults(): Map<String, String> {
    defaultsCache?.let { return it }
    val map = mutableMapOf<String, String>()
    try {
        val parser: XmlResourceParser = Env.getApp().resources.getXml(R.xml.remote_config_defaults)
        var tag: String? = null
        var key: String? = null
        var value: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> tag = parser.name
                XmlPullParser.TEXT -> when (tag) {
                    "key" -> key = parser.text
                    "value" -> value = parser.text
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry") {
                        if (key != null && value != null) map[key.trim()] = value
                        key = null
                        value = null
                    }
                    tag = null
                }
            }
            event = parser.next()
        }
        parser.close()
    } catch (e: Exception) {
        loge("FirebaseConfig", "failed to read remote_config_defaults.xml: ${e.message}")
    }
    defaultsCache = map
    return map
}

private fun onConfigLoadFinish() {
    AppConfig.sync()
}
