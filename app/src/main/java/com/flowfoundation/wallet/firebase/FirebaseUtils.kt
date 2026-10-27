package com.flowfoundation.wallet.firebase

import android.app.Application
import com.flowfoundation.wallet.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.flowfoundation.wallet.firebase.messaging.uploadPushToken
import com.flowfoundation.wallet.utils.logd


private const val TAG = "Firebase"

fun firebaseInformationCheck() {
    uploadPushToken(isNewToken = true)
}

fun firebaseInitialize(application: Application) {
    if (!FirebaseFlags.enabled) {
        logd(TAG, "Firebase disabled (FIREBASE_ENABLED=false): skipping FirebaseApp, App Check, Crashlytics, Analytics, Messaging")
        return
    }
    FirebaseApp.initializeApp(application) ?: return
    // Collection is off by default in the manifest so a build without a live project stays quiet.
    runCatching { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true) }
    runCatching { com.google.firebase.analytics.FirebaseAnalytics.getInstance(application).setAnalyticsCollectionEnabled(true) }
    runCatching { com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled = true }
    setupAppCheck()
}

private fun setupAppCheck() {
    FirebaseAppCheck.getInstance().apply {
        installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        if (BuildConfig.DEBUG) {
            installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        }
        setTokenAutoRefreshEnabled(true)
        addAppCheckListener { token ->
            logd(TAG, "AppCheck token: $token")
        }
    }
}
