package com.flowfoundation.wallet.reactnative
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.flowfoundation.wallet.reactnative.bridge.QRCodeScanManager
import com.flowfoundation.wallet.reactnative.bridge.RNBridge
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.reactnative.bridge.createWalletAccountFromAddress
import com.flowfoundation.wallet.wallet.toAddress
import com.google.gson.Gson

class ReactNativeActivity : ReactActivity() {

    override fun getMainComponentName(): String = "FRWRN"

    /**
     * Returns the instance of the ReactActivityDelegate. Here we use a util class
     * DefaultReactActivityDelegate which allows you to easily enable Fabric and Concurrent React
     * (aka React 18) with two boolean flags.
     */
    override fun createReactActivityDelegate(): ReactActivityDelegate {
        return object : DefaultReactActivityDelegate(
            this,
            mainComponentName,
            false, // fabricEnabled
        ) {
            override fun getLaunchOptions(): Bundle? {
                val launchOptions = Bundle()

                intent?.let { intent ->
                    val address = intent.getStringExtra("address")
                    val network = intent.getStringExtra("network")
                    val initialRoute = intent.getStringExtra("initialRoute")
                    val screen = intent.getStringExtra("screen")
                    val sendToConfigJson = intent.getStringExtra("sendToConfig")

                    // Top level props
                    address?.let {
                        launchOptions.putString("address", it)
                        Log.d(TAG, "Added address to launch options: $it")
                    }
                    network?.let {
                        launchOptions.putString("network", it)
                        Log.d(TAG, "Added network to launch options: $it")
                    }
                    initialRoute?.let {
                        launchOptions.putString("initialRoute", it)
                        Log.d(TAG, "Added initialRoute to launch options: $it")
                    }

                    // Create initialProps object if we have screen or sendToConfig
                    if (screen != null || sendToConfigJson != null) {
                        val initialPropsBundle = Bundle()

                        screen?.let {
                            initialPropsBundle.putString("screen", it)
                            Log.d(TAG, "Added screen to initialProps: $it")
                        }
                        sendToConfigJson?.let { jsonString ->
                            Log.d(TAG, "Processing sendToConfig JSON: $jsonString")
                            initialPropsBundle.putString("sendToConfig", jsonString)
                        }

                        launchOptions.putBundle("initialProps", initialPropsBundle)
                        Log.d(TAG, "Added initialProps bundle with ${initialPropsBundle.size()} properties")
                    }
                }

                Log.d(TAG, "Launch options created with ${launchOptions.size()} properties")
                return launchOptions
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate called")
        super.onCreate(savedInstanceState)

        // Log the intent extras for debugging
        intent?.let {
            Log.d(TAG, "Intent extras:")
            Log.d(TAG, "  address: ${it.getStringExtra("address")}")
            Log.d(TAG, "  network: ${it.getStringExtra("network")}")
            Log.d(TAG, "  initialRoute: ${it.getStringExtra("initialRoute")}")
            Log.d(TAG, "  screen: ${it.getStringExtra("screen")}")
            Log.d(TAG, "  sendToConfig: ${it.getStringExtra("sendToConfig")}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Handle QR scan result
        QRCodeScanManager.handleScanResult(resultCode, data)
    }

    companion object {
        private const val TAG = "ReactNativeActivity"

        /**
         * Determine the route name based on screen type and sendToConfig
         */
        private fun getRouteName(screenType: RNBridge.ScreenType, sendToConfig: RNBridge.SendToConfig?): String {
            return when (screenType) {
                RNBridge.ScreenType.SEND_ASSET -> {
                    sendToConfig?.let { config ->
                        when {
                            // If both targetAddress and selectedToken exist, go to sendToken
                            config.targetAddress != null && config.selectedToken != null -> "SendTokens"
                            // If only selectedToken exists, go to selectAddress
                            config.selectedToken != null -> "SendTo"
                            // If selectedNFTs exist and not empty, go to selectAddress
                            config.selectedNFTs != null && config.selectedNFTs.isNotEmpty() -> "SendTo"
                            // Otherwise, go to selectAssets
                            else -> "SelectTokens"
                        }
                    } ?: "SelectTokens"
                }
                RNBridge.ScreenType.TOKEN_DETAIL -> "Home"
            }
        }

        /**
         * Launch the React Native Demo Activity
         */
        fun launch(context: Context) {
            launch(context, null, null, null)
        }

        /**
         * Launch the React Native Demo Activity with default address and network
         */
        fun launch(context: Context, screenType: RNBridge.ScreenType?) {
            val address = WalletManager.selectedWalletAddress().toAddress()
            val network = chainNetWorkString()
            launch(context, screenType, address, network)
        }

        /**
         * Launch the React Native Demo Activity with parameters
         */
        fun launch(context: Context, screenType: RNBridge.ScreenType?, address: String?, network: String?) {
            Log.d(TAG, "Launching ReactNativeActivity with params:")
            Log.d(TAG, "  screenType: $screenType")
            Log.d(TAG, "  address: $address")
            Log.d(TAG, "  network: $network")

            val intent = Intent(context, ReactNativeActivity::class.java)

            // Add flags to ensure the activity comes to the foreground prominently
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

            address?.let {
                intent.putExtra("address", it)
            }
            network?.let {
                intent.putExtra("network", it)
            }
            screenType?.let {
                // Convert screen enum to string and determine route based on screen type
                val screenString = if (it == RNBridge.ScreenType.SEND_ASSET) "send-asset" else "token-detail"
                val routeName = getRouteName(it, null)

                intent.putExtra("screen", screenString)
                intent.putExtra("initialRoute", routeName)
            }
            context.startActivity(intent)
        }

        /**
         * Launch with InitialProps containing screen and SendToConfig
         */
        fun launchWithConfig(context: Context, screenType: RNBridge.ScreenType, sendToConfig: RNBridge.SendToConfig?, address: String?, network: String?) {
            Log.d(TAG, "Launching ReactNativeActivity with config:")
            Log.d(TAG, "  screenType: $screenType")
            Log.d(TAG, "  address: $address")
            Log.d(TAG, "  network: $network")

            val intent = Intent(context, ReactNativeActivity::class.java)

            // Add flags to ensure the activity comes to the foreground prominently
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

            address?.let {
                intent.putExtra("address", it)
            }
            network?.let {
                intent.putExtra("network", it)
            }

            // Convert screen enum to string and determine route based on screen type and config
            val screenString = if (screenType == RNBridge.ScreenType.SEND_ASSET) "send-asset" else "token-detail"
            val routeName = getRouteName(screenType, sendToConfig)

            intent.putExtra("screen", screenString)
            intent.putExtra("initialRoute", routeName)

            // Serialize SendToConfig to JSON
            sendToConfig?.let {
                val sendToConfigJson = Gson().toJson(it)
                intent.putExtra("sendToConfig", sendToConfigJson)
                Log.d(TAG, "sendToConfig JSON: $sendToConfigJson")
            }

            context.startActivity(intent)
        }

        /**
         * Convenience method for token send with default address and network
         */
        fun launchTokenSend(context: Context, token: RNBridge.TokenModel) {
            val address = WalletManager.selectedWalletAddress().toAddress()
            val network = chainNetWorkString()
            val fromAccount = createWalletAccountFromAddress(address)
            val sendToConfig = RNBridge.SendToConfig(token, fromAccount, null, null)
            launchWithConfig(context, RNBridge.ScreenType.SEND_ASSET, sendToConfig, address, network)
        }

        /**
         * Convenience method for NFT send with default address and network
         */
        fun launchNFTSend(context: Context, nfts: List<RNBridge.NFTModel>) {
            val address = WalletManager.selectedWalletAddress().toAddress()
            val network = chainNetWorkString()
            val fromAccount = createWalletAccountFromAddress(address)
            val sendToConfig = RNBridge.SendToConfig(null, fromAccount, nfts, null)
            launchWithConfig(context, RNBridge.ScreenType.SEND_ASSET, sendToConfig, address, network)
        }

        /**
         * Convenience method for Wallet send with default address and network
         * @param fromAccount can be null - if null, will generate from selected wallet address
         * @param targetAddress can be null - if null, user will select target in RN
         */
        fun launchWalletSend(context: Context, fromAccount: RNBridge.WalletAccount?, targetAddress: String?) {
            val address = WalletManager.selectedWalletAddress().toAddress()
            val network = chainNetWorkString()
            val finalFromAccount = fromAccount ?: createWalletAccountFromAddress(address)
            val sendToConfig = RNBridge.SendToConfig(null, finalFromAccount, null, targetAddress)
            launchWithConfig(context, RNBridge.ScreenType.SEND_ASSET, sendToConfig, address, network)
        }

        /**
         * Launch the Receive screen with default address and network
         */
        fun launchReceive(context: Context) {
            Log.d(TAG, "Launching ReactNativeActivity for Receive screen")

            val address = WalletManager.selectedWalletAddress().toAddress()
            val network = chainNetWorkString()

            val intent = Intent(context, ReactNativeActivity::class.java)

            // Add flags to ensure the activity comes to the foreground prominently
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

            intent.putExtra("address", address)
            intent.putExtra("network", network)
            intent.putExtra("initialRoute", "Receive")

            Log.d(TAG, "Launching Receive screen with address: $address, network: $network")
            context.startActivity(intent)
        }
    }
}
