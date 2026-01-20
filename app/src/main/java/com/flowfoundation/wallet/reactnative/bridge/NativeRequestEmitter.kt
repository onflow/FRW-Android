package com.flowfoundation.wallet.reactnative.bridge

import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.flowfoundation.wallet.manager.app.ActivityManager
import com.flowfoundation.wallet.utils.logw

object NativeRequestEmitter {
    private const val TAG = "NativeRequestEmitter"

    fun emit(requestId: String, eventName: String, paramsJson: String) {
        val reactContext = ActivityManager.getReactContext()
        if (reactContext == null) {
            logw(TAG, "React context not available, skipping nativeRequest emit")
            return
        }

        val payload = Arguments.createMap().apply {
            putString("requestId", requestId)
            putString("eventName", eventName)
            putString("paramsJson", paramsJson)
        }

        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("nativeRequest", payload)
    }
}
