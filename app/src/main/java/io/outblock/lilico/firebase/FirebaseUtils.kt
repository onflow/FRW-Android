package io.outblock.lilico.firebase

import android.app.Application
import com.google.firebase.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import io.outblock.lilico.firebase.messaging.uploadPushToken
import io.outblock.lilico.utils.logd


private const val TAG = "Firebase"

fun firebaseInformationCheck() {
    uploadPushToken()
}

fun firebaseInitialize(application: Application) {
    FirebaseApp.initializeApp(application)
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