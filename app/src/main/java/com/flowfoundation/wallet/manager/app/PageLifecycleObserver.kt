package com.flowfoundation.wallet.manager.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

class PageLifecycleObserver : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    companion object {
        fun init(application: Application) {
            application.registerActivityLifecycleCallbacks(PageLifecycleObserver())
        }
    }
}