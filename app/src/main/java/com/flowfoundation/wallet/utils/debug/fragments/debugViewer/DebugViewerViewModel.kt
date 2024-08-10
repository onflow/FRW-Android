package com.flowfoundation.wallet.utils.debug.fragments.debugViewer

import android.graphics.Point
import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.utils.debug.DebugManager
import com.google.gson.Gson

class DebugViewerViewModel : ViewModel() {
    var collapsed: Boolean
        get() {
            return DebugManager.getSharedPrefs().getBoolean("DEBUG_VIEWER_COLLAPSED", false)
        }
        set(value) {
            DebugManager.getSharedPrefs().edit().putBoolean("DEBUG_VIEWER_COLLAPSED", value).apply()
        }

    var position: Point?
        get() {
            DebugManager.getSharedPrefs().getString("DEBUG_VIEWER_POSITION", null)?.let {
                return Gson().fromJson(it, Point::class.java)
            }
            return null
        }
        set(value) {
            DebugManager.getSharedPrefs().edit().putString("DEBUG_VIEWER_POSITION", Gson().toJson(value)).apply()
        }

    var size: Point?
        get() {
            DebugManager.getSharedPrefs().getString("DEBUG_VIEWER_SIZE", null)?.let {
                return Gson().fromJson(it, Point::class.java)
            }
            return null
        }
        set(value) {
            DebugManager.getSharedPrefs().edit().putString("DEBUG_VIEWER_SIZE", Gson().toJson(value)).apply()
        }
    var dX = 0f
    var dY = 0f
    var startX = 0f
    var startY = 0f
    var resizeDX = 0f
    var resizeDY = 0f
}
