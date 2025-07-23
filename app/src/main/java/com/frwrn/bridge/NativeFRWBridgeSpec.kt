package com.frwrn.bridge

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.Promise
import com.facebook.react.turbomodule.core.interfaces.TurboModule

/**
 * Base specification class for the NativeFRWBridge TurboModule
 * 
 * This abstract class defines the interface that the native implementation must follow.
 * It extends ReactContextBaseJavaModule and implements TurboModule to work with 
 * React Native's new architecture.
 */
abstract class NativeFRWBridgeSpec(reactContext: ReactApplicationContext) : 
    ReactContextBaseJavaModule(reactContext), TurboModule {

    /**
     * Get the currently selected wallet address
     * @return The selected wallet address as a hex string, or null if no wallet is available
     */
    abstract fun getSelectedAddress(): String?

    /**
     * Get the current Flow network configuration
     * @return The current network (mainnet, testnet, etc.)
     */
    abstract fun getNetwork(): String?

    /**
     * Get the current Firebase JWT token
     * @param promise Promise to resolve with the JWT token
     */
    abstract fun getJWT(promise: Promise)

    /**
     * Sign hex-encoded data using the current wallet's private key
     * @param hexData The hex-encoded data to sign
     * @param promise Promise to resolve with the signature
     */
    abstract fun sign(hexData: String, promise: Promise)

    /**
     * Get the address book contacts
     * @param promise Promise to resolve with the address book contacts as JSON string
     */
    abstract fun getAddressBook(promise: Promise)

    /**
     * Get recent transaction contacts
     * @param promise Promise to resolve with the recent contacts as JSON string  
     */
    abstract fun getRecentContacts(promise: Promise)

    /**
     * Get wallet accounts (main account + child accounts + EVM account)
     * @param promise Promise to resolve with the wallet accounts as JSON string
     */
    abstract fun getWalletAccounts(promise: Promise)

    /**
     * Get COA (EVM) FLOW balance
     * @param promise Promise to resolve with the COA FLOW balance as string
     */
    abstract fun getCOAFlowBalance(promise: Promise)
} 