package com.flowfoundation.wallet.base.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.BaseContextWrappingDelegate
import java.lang.ref.WeakReference

open class BaseActivity : AppCompatActivity() {
    private var firstVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        currentActivity = WeakReference(this)
        super.onCreate(savedInstanceState)
    }

    override fun getDelegate() = BaseContextWrappingDelegate(super.getDelegate())

    override fun onResume() {
        currentActivity = WeakReference(this)
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