package com.flowfoundation.wallet.firebase

import com.flowfoundation.wallet.BuildConfig

/**
 * Master switch for every Firebase SDK (Messaging, Remote Config, Crashlytics, Analytics,
 * Installations, App Check, Storage). Off by default: Firebase project lilico-334404 is gone, so the
 * app must start and work without it. Set `firebaseEnabled=true` in gradle.properties (or
 * -PfirebaseEnabled=true) only for a build with a google-services.json from a live project.
 *
 * Firebase Auth is not behind this flag: it was replaced by FRW-Server tokens (firebase/auth).
 */
object FirebaseFlags {
    val enabled: Boolean get() = BuildConfig.FIREBASE_ENABLED
}
