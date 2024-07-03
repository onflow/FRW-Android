package com.flowfoundation.wallet.base.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.BaseContextWrappingDelegate
import java.lang.ref.WeakReference

open class BaseActivity : AppCompatActivity() {
    private var firstVisible = true


//    override fun attachBaseContext(newBase: Context?) {
//        super.attachBaseContext(Translized.wrapContext(newBase))
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        currentActivity = WeakReference(this)
        super.onCreate(savedInstanceState)

//        if (!BuildConfig.DEBUG) {
//            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
//        }
    }

    override fun getDelegate() = BaseContextWrappingDelegate(super.getDelegate())

    override fun onResume() {
        currentActivity = WeakReference(this)
        super.onResume()
        if (firstVisible) {
            onFirstVisible()
        } else {
            onRevisible()
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

    open fun onRevisible() {}

    fun isFirstVisible() = firstVisible

    companion object {
        private var currentActivity: WeakReference<BaseActivity>? = null

        fun getCurrentActivity(): BaseActivity? {
            return currentActivity?.get()
        }
    }
}