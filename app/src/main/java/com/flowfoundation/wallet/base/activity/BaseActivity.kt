package com.flowfoundation.wallet.base.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.BaseContextWrappingDelegate
import com.flowfoundation.wallet.manager.app.ActivityManager
import java.lang.ref.WeakReference

open class BaseActivity : AppCompatActivity() {
    private var firstVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        currentActivity = WeakReference(this)
        ActivityManager.setCurrentActivity(this)  // Register with ActivityManager
        super.onCreate(savedInstanceState)
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