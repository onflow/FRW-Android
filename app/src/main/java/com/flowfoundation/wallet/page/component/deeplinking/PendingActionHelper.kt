package com.flowfoundation.wallet.page.component.deeplinking

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logd


object PendingActionHelper {
    private const val PREFS_NAME = "pending_action_prefs"
    private const val KEY_PENDING_DEEPLINK = "pending_deeplink"
    private const val KEY_HAS_PENDING_DEEPLINK = "has_pending_deeplink"
    private const val TAG = "PendingActionHelper"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun savePendingDeepLink(context: Context, deepLink: Uri) {
        logd(TAG, "executeDeepLinking: savePendingDeepLink")
        try {
            getPrefs(context).edit().apply {
                putString(KEY_PENDING_DEEPLINK, deepLink.toString())
                putBoolean(KEY_HAS_PENDING_DEEPLINK, true)
                commit()
            }
        } catch (e: Exception) {
            loge(TAG, "Error saving pending deep link: ${e.message}")
            loge(e)
        }
    }

    fun hasPendingDeepLink(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(KEY_HAS_PENDING_DEEPLINK, false)
        } catch (e: Exception) {
            loge(TAG, "Error checking pending deep link: ${e.message}")
            loge(e)
            false
        }
    }

    fun getPendingDeepLink(context: Context): Uri? {
        try {
            val deepLinkStr = getPrefs(context).getString(KEY_PENDING_DEEPLINK, null) ?: return null
            return Uri.parse(deepLinkStr)
        } catch (e: Exception) {
            loge(TAG, "Error retrieving pending deep link: ${e.message}")
            loge(e)
            return null
        }
    }

    fun clearPendingDeepLink(context: Context) {
        try {
            getPrefs(context).edit().apply {
                remove(KEY_PENDING_DEEPLINK)
                putBoolean(KEY_HAS_PENDING_DEEPLINK, false)
                commit()
            }
        } catch (e: Exception) {
            loge(TAG, "Error clearing pending deep link: ${e.message}")
            loge(e)
        }
    }
}