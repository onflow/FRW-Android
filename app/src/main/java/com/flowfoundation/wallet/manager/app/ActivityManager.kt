package com.flowfoundation.wallet.manager.app

import android.app.Activity
import com.facebook.react.bridge.ReactApplicationContext
import com.flowfoundation.wallet.utils.logd
import java.lang.ref.WeakReference

/**
 * ActivityManager - Manages the current activity reference for React Native
 *
 * This singleton provides access to the current activity for components that need it,
 * particularly for showing alerts and dialogs from non-UI threads.
 */
object ActivityManager {
    private const val TAG = "ActivityManager"

    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var reactContextRef: WeakReference<ReactApplicationContext>? = null

    /**
     * Set the current activity reference
     * Should be called from Activity lifecycle methods
     */
    @JvmStatic
    fun setCurrentActivity(activity: Activity?) {
        logd(TAG, "Setting current activity: ${activity?.javaClass?.simpleName}")
        currentActivityRef = if (activity != null) {
            WeakReference(activity)
        } else {
            null
        }
    }

    /**
     * Get the current activity
     * Returns null if no activity is available or if it has been garbage collected
     */
    @JvmStatic
    fun getCurrentActivity(): Activity? {
        val activity = currentActivityRef?.get()
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            return activity
        }

        // Fallback to React Native context's current activity
        val reactContext = reactContextRef?.get()
        if (reactContext != null) {
            val rnActivity = reactContext.currentActivity
            if (rnActivity != null && !rnActivity.isFinishing && !rnActivity.isDestroyed) {
                logd(TAG, "Using React Native current activity: ${rnActivity.javaClass.simpleName}")
                return rnActivity
            }
        }

        logd(TAG, "No valid current activity available")
        return null
    }

    /**
     * Set the React application context
     * Should be called from React Native module initialization
     */
    @JvmStatic
    fun setReactContext(context: ReactApplicationContext?) {
        logd(TAG, "Setting React context")
        reactContextRef = if (context != null) {
            WeakReference(context)
        } else {
            null
        }
    }

    @JvmStatic
    fun getReactContext(): ReactApplicationContext? {
        return reactContextRef?.get()
    }

    /**
     * Clear all references
     * Should be called when the application is being destroyed
     */
    @JvmStatic
    fun clear() {
        logd(TAG, "Clearing all activity references")
        currentActivityRef = null
        reactContextRef = null
    }

    /**
     * Check if an activity is available
     */
    @JvmStatic
    fun hasActivity(): Boolean {
        return getCurrentActivity() != null
    }
}
