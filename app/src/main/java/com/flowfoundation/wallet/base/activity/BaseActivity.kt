package com.flowfoundation.wallet.base.activity

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.BaseContextWrappingDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.flowfoundation.wallet.manager.app.ActivityManager
import java.lang.ref.WeakReference

open class BaseActivity : AppCompatActivity() {
    private var firstVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        currentActivity = WeakReference(this)
        ActivityManager.setCurrentActivity(this)
        // Opt into edge-to-edge early. On API 35+ this is enforced by the system anyway.
        // On API 28-34 this is needed so our insets listener is the sole source of nav bar padding.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // UltimateBarX fitWindow(true) calls in subclass onCreate may set
        // setDecorFitsSystemWindows(true) after our onCreate. Reset it here so our
        // insets listener is always in control of navigation bar padding.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyNavigationBarInsets()
    }

    /**
     * Override to return true when the activity/layout handles nav bar insets itself
     * (e.g., via a direct ViewCompat.setOnApplyWindowInsetsListener on the root view).
     * BaseActivity will skip its android.R.id.content padding to prevent double-padding.
     */
    protected open fun handlesInsetsNatively(): Boolean = false

    /**
     * Adds bottom padding equal to the navigation bar height on android.R.id.content.
     * Listener is attached to window.decorView — the top of the view hierarchy — so it
     * receives insets before AppCompat's internal FitWindowsLinearLayout can consume them.
     * Works for both 3-button nav (full bar height) and gesture nav (indicator height or 0).
     * Status bar is intentionally excluded — handled by existing UltimateBarX calls per Activity.
     */
    private fun applyNavigationBarInsets() {
        if (handlesInsetsNatively()) return
        val contentView = window.decorView.findViewById<View>(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            contentView.updatePadding(bottom = navBar.bottom)
            insets  // Return without consuming — let normal child dispatch continue
        }
        // Trigger an immediate insets dispatch now that the listener is set up and
        // setDecorFitsSystemWindows(false) is in its final state.
        ViewCompat.requestApplyInsets(window.decorView)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-trigger insets dispatch once the window is actually visible and has valid insets.
        // requestApplyInsets() in onPostCreate fires before the window is shown (pre-WindowManager
        // attach), so it may receive zero insets. Activities without UltimateBarX have no other
        // insets trigger, so this guarantees the listener fires at least once with real values.
        if (hasFocus) {
            ViewCompat.requestApplyInsets(window.decorView)
        }
    }

    override fun getDelegate() = BaseContextWrappingDelegate(super.getDelegate())

    override fun onResume() {
        currentActivity = WeakReference(this)
        ActivityManager.setCurrentActivity(this)  // Update ActivityManager on resume
        super.onResume()
        if (firstVisible) {
            onFirstVisible()
        } else {
            onReVisible()
        }
        firstVisible = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentActivity?.get() == this) {
            currentActivity = null
            ActivityManager.setCurrentActivity(null)  // Clear ActivityManager on destroy
        }
    }

    open fun onFirstVisible() {}

    open fun onReVisible() {}

    fun isFirstVisible() = firstVisible

    companion object {
        private var currentActivity: WeakReference<BaseActivity>? = null

        fun getCurrentActivity(): BaseActivity? {
            return currentActivity?.get()
        }
    }
}
