package com.flowfoundation.wallet.page.component.deeplinking

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.flowfoundation.wallet.utils.logd


object PendingActionHelper {
    private const val PREFS_NAME = "pending_action_prefs"
    private const val KEY_PENDING_DEEPLINK = "pending_deeplink"
    private const val KEY_HAS_PENDING_DEEPLINK = "has_pending_deeplink"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun savePendingDeepLink(context: Context, deepLink: Uri) {
        logd("DeepLinkingDispatch", "executeDeepLinking: savePendingDeepLink")
        getPrefs(context).edit().apply {
            putString(KEY_PENDING_DEEPLINK, deepLink.toString())
            putBoolean(KEY_HAS_PENDING_DEEPLINK, true)
            commit()
        }
    }

    fun hasPendingDeepLink(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAS_PENDING_DEEPLINK, false)
    }

    fun getPendingDeepLink(context: Context): Uri? {
        val deepLinkStr = getPrefs(context).getString(KEY_PENDING_DEEPLINK, null) ?: return null
        return Uri.parse(deepLinkStr)
    }

    fun clearPendingDeepLink(context: Context) {
        getPrefs(context).edit().apply {
            remove(KEY_PENDING_DEEPLINK)
            putBoolean(KEY_HAS_PENDING_DEEPLINK, false)
            commit()
        }
    }
}