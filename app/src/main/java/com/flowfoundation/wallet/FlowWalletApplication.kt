package com.flowfoundation.wallet

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import com.flowfoundation.wallet.crowdin.crowdinInitialize
import com.flowfoundation.wallet.manager.LaunchManager
import com.flowfoundation.wallet.utils.Env

class FlowWalletApplication : Application(), ReactApplication {

    override val reactNativeHost: ReactNativeHost =
        object : DefaultReactNativeHost(this) {
            override fun getPackages(): List<ReactPackage> =
                PackageList(this).packages.apply {
                    // Packages that cannot be autolinked yet can be added manually here, for example:
                    // add(MyReactNativePackage())
                    
                    // Add EnvConfigModule
                    add(object : ReactPackage {
                        override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
                            return listOf(EnvConfigModule(reactContext))
                        }
                        
                        override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
                            return emptyList()
                        }
                    })
                }

            override fun getJSMainModuleName(): String = "index"

            override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

            override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
            override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
        }

    override val reactHost: ReactHost
        get() = getDefaultReactHost(applicationContext, reactNativeHost)

    override fun onCreate() {
        super.onCreate()
        loadReactNative(this)
        Env.init(this)
        crowdinInitialize(this)
        LaunchManager.init(this)
    }
}
