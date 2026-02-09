package com.flowfoundation.wallet.reactnative.bridge

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule

class NativeRequestEventEmitter(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    override fun getName(): String {
        return NAME
    }

    companion object {
        const val NAME = "NativeRequestEventEmitter"
    }
}
