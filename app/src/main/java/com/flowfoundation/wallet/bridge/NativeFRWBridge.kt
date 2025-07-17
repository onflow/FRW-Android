package com.flowfoundation.wallet.bridge

import android.content.Context
import com.flowfoundation.wallet.bridge.NativeFRWBridgeSpec
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.Promise;

class NativeFRWBridge(reactContext: ReactApplicationContext) : NativeFRWBridgeSpec(reactContext) {

    override fun getName() = NAME

    override fun getSelectedAddress(): String? {
        return "0x123321"
    }

    override fun getNetwork(): String? {
        return "Mainnet"
    }

    override fun getJWT(promise: Promise) {
        promise.resolve("This is android Native JWT")
    }

    companion object {
        const val NAME = "NativeFRWBridge"
    }
}