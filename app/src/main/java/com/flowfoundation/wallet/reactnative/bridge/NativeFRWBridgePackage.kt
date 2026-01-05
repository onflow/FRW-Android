package com.flowfoundation.wallet.reactnative.bridge

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class NativeFRWBridgePackage : BaseReactPackage() {

    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
        when (name) {
            NativeFRWBridge.NAME -> NativeFRWBridge(reactContext)
            NativeRequestEventEmitter.NAME -> NativeRequestEventEmitter(reactContext)
            else -> null
        }

    override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
        mapOf(
            NativeFRWBridge.NAME to ReactModuleInfo(
                name = NativeFRWBridge.NAME,
                className = NativeFRWBridge.NAME,
                canOverrideExistingModule = false,
                needsEagerInit = false,
                isCxxModule = false,
                isTurboModule = true
            ),
            NativeRequestEventEmitter.NAME to ReactModuleInfo(
                name = NativeRequestEventEmitter.NAME,
                className = NativeRequestEventEmitter.NAME,
                canOverrideExistingModule = false,
                needsEagerInit = false,
                isCxxModule = false,
                isTurboModule = false
            )
        )
    }
}
