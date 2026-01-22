package com.flowfoundation.wallet.reactnative
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.flowfoundation.wallet.reactnative.bridge.QRCodeScanManager
import com.flowfoundation.wallet.reactnative.bridge.RNBridge
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.reactnative.bridge.createWalletAccountFromAddress
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.wallet.toAddress
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

private val RNBridge.ScreenType.value: String
    get() = try {
        RNBridge.ScreenType::class.java.getField(this.name).getAnnotation(SerializedName::class.java)?.value ?: this.toString()
    } catch (e: Exception) {
        this.toString()
    }

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
                        logd(TAG, "Added address to launch options: $it")
                    }
                    network?.let {
                        launchOptions.putString("network", it)
                        logd(TAG, "Added network to launch options: $it")
                    }
                    initialRoute?.let {
                        launchOptions.putString("initialRoute", it)
                        logd(TAG, "Added initialRoute to launch options: $it")
                    }

                    // Create initialProps object if we have screen or sendToConfig
                    if (screen != null || sendToConfigJson != null) {
                        val initialPropsBundle = Bundle()

                        screen?.let {
                            initialPropsBundle.putString("screen", it)
                            logd(TAG, "Added screen to initialProps: $it")
                        }
                        sendToConfigJson?.let { jsonString ->
                            logd(TAG, "Processing sendToConfig JSON: $jsonString")
                            initialPropsBundle.putString("sendToConfig", jsonString)
                        }

                        launchOptions.putBundle("initialProps", initialPropsBundle)
                        logd(TAG, "Added initialProps bundle with ${initialPropsBundle.size()} " +
                          "properties")
                    }
                }

                logd(TAG, "Launch options created with ${launchOptions.size()} properties")
                return launchOptions
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logd(TAG, "onCreate called")
        super.onCreate(savedInstanceState)

        // Log the intent extras for debugging
        intent?.let {
            logd(TAG, "Intent extras:")
            logd(TAG, "  address: ${it.getStringExtra("address")}")
            logd(TAG, "  network: ${it.getStringExtra("network")}")
            logd(TAG, "  initialRoute: ${it.getStringExtra("initialRoute")}")
            logd(TAG, "  screen: ${it.getStringExtra("screen")}")
            logd(TAG, "  sendToConfig: ${it.getStringExtra("sendToConfig")}")
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
                RNBridge.ScreenType.BACKUP_TIP -> "KeyRotationTip"
                RNBridge.ScreenType.KEYSTORE_MIGRATION -> "KeystoreMigrationTip"
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
            logd(TAG, "Launching ReactNativeActivity with params:")
            logd(TAG, "  screenType: $screenType")
            logd(TAG, "  address: $address")
            logd(TAG, "  network: $network")

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
                val screenString = it.value
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
            logd(TAG, "Launching ReactNativeActivity with config:")
            logd(TAG, "  screenType: $screenType")
            logd(TAG, "  address: $address")
            logd(TAG, "  network: $network")

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
            val screenString = screenType.value
            val routeName = getRouteName(screenType, sendToConfig)

            intent.putExtra("screen", screenString)
            intent.putExtra("initialRoute", routeName)

            // Serialize SendToConfig to JSON
            sendToConfig?.let {
                val sendToConfigJson = Gson().toJson(it)
                intent.putExtra("sendToConfig", sendToConfigJson)
                logd(TAG, "sendToConfig JSON: $sendToConfigJson")
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
    }
}
