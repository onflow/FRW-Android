package com.frwrn.bridge

import com.facebook.react.TurboReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.turbomodule.core.interfaces.TurboModule

/**
 * TurboModule package for the FRW Bridge
 * 
 * This package is responsible for registering and providing the NativeFRWBridge
 * TurboModule to React Native's module system.
 */
class FRWBridgePackage : TurboReactPackage() {

    /**
     * Get the TurboModule instance for the given name
     * 
     * @param name The name of the module to create
     * @param reactContext The React application context
     * @return TurboModule instance or null if the name doesn't match
     */
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
        return when (name) {
            NativeFRWBridgeModule.NAME -> NativeFRWBridgeModule(reactContext)
            else -> null
        }
    }

    /**
     * Provide information about the available React modules
     * 
     * @return ReactModuleInfoProvider that describes our modules
     */
    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
        return ReactModuleInfoProvider {
            mapOf(
                NativeFRWBridgeModule.NAME to ReactModuleInfo(
                    NativeFRWBridgeModule.NAME,
                    NativeFRWBridgeModule::class.java.name,
                    false, // canOverrideExistingModule
                    false, // needsEagerInit
                    true,  // hasConstants
                    false, // isCxxModule
                    true   // isTurboModule
                )
            )
        }
    }
} 