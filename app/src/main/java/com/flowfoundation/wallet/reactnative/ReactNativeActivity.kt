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
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.utils.logd
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

                    address?.let {
                        launchOptions.putString("address", it)
                    }
                    network?.let {
                        launchOptions.putString("network", it)
                    }
                    initialRoute?.let {
                        launchOptions.putString("initialRoute", it)
                    }

                    // Create initialProps object if we have screen or sendToConfig
                    if (screen != null || sendToConfigJson != null) {
                        val initialPropsBundle = Bundle()

                        screen?.let {
                            initialPropsBundle.putString("screen", it)
                        }
                        sendToConfigJson?.let { jsonString ->
                            initialPropsBundle.putString("sendToConfig", jsonString)
                        }

                        launchOptions.putBundle("initialProps", initialPropsBundle)
                    }
                }
                return launchOptions
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Handle QR scan result
        QRCodeScanManager.handleScanResult(resultCode, data)
    }

    companion object {
        private const val TAG = "ReactNativeActivity"

        /**
         * Get the screen name string from ScreenType enum
         */
        private fun getScreenName(screenType: RNBridge.ScreenType): String {
            return when (screenType) {
                RNBridge.ScreenType.SEND_ASSET -> "send-asset"
                RNBridge.ScreenType.TOKEN_DETAIL -> "token-detail"
                RNBridge.ScreenType.ONBOARDING -> "onboarding"
                RNBridge.ScreenType.RECEIVE -> "receive"
                RNBridge.ScreenType.ONBOARDING -> "onboarding"
            }
        }

        /**
         * Determine the route name based on screen type and sendToConfig
         */
        private fun getRouteName(screenType: RNBridge.ScreenType, sendToConfig: RNBridge.SendToConfig?): String {
            return when (screenType) {
                RNBridge.ScreenType.SEND_ASSET -> {
                    sendToConfig?.let { config ->
                        when {
                            // If both targetAddress and selectedToken exist, go to sendToken
                            config.targetAddress != null && config.selectedToken != null -> RNBridge.InitialRoute.SEND_TOKENS.routeName
                            // If only selectedToken exists, go to selectAddress
                            config.selectedToken != null -> RNBridge.InitialRoute.SEND_TO.routeName
                            // If selectedNFTs exist and not empty, go to selectAddress
                            config.selectedNFTs != null && config.selectedNFTs.isNotEmpty() -> RNBridge.InitialRoute.SEND_TO.routeName
                            // Otherwise, go to selectAssets
                            else -> RNBridge.InitialRoute.SELECT_TOKENS.routeName
                        }
                    } ?: RNBridge.InitialRoute.SELECT_TOKENS.routeName
                }
                RNBridge.ScreenType.TOKEN_DETAIL -> RNBridge.InitialRoute.HOME.routeName
                RNBridge.ScreenType.ONBOARDING -> RNBridge.InitialRoute.GET_STARTED.routeName
                RNBridge.ScreenType.RECEIVE -> "Receive"
                RNBridge.ScreenType.ONBOARDING -> "Onboarding"
            }
        }

        /**
         * Launch with specific screen type and initial route using enums
         */
        fun launchWithRoute(context: Context, screenType: RNBridge.ScreenType, initialRoute: RNBridge.InitialRoute) {
            val intent = Intent(context, ReactNativeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            intent.putExtra("screen", getScreenName(screenType))
            intent.putExtra("initialRoute", initialRoute.routeName)
            context.startActivity(intent)
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
            val intent = Intent(context, ReactNativeActivity::class.java)

            // Add flags to ensure only one instance of ReactNativeActivity exists
            // SINGLE_TOP prevents creating a new instance if one already exists at the top of the stack
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

            address?.let {
                intent.putExtra("address", it)
            }
            network?.let {
                intent.putExtra("network", it)
            }
            screenType?.let { type ->
                // Use screenName property from enum and determine route based on screen type
                val routeName = getRouteName(type, null)

                intent.putExtra("screen", getScreenName(type))
                intent.putExtra("initialRoute", routeName)
            }
            context.startActivity(intent)
        }

        /**
         * Launch with InitialProps containing screen and SendToConfig
         */
        fun launchWithConfig(context: Context, screenType: RNBridge.ScreenType, sendToConfig: RNBridge.SendToConfig?, address: String?, network: String?) {
            val intent = Intent(context, ReactNativeActivity::class.java)

            // Add flags to ensure only one instance of ReactNativeActivity exists
            // SINGLE_TOP prevents creating a new instance if one already exists at the top of the stack
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

            address?.let {
                intent.putExtra("address", it)
            }
            network?.let {
                intent.putExtra("network", it)
            }

            // Use screenName property from enum and determine route based on screen type and config
            val routeName = getRouteName(screenType, sendToConfig)

            intent.putExtra("screen", getScreenName(screenType))
            intent.putExtra("initialRoute", routeName)

            // Serialize SendToConfig to JSON
            sendToConfig?.let {
                val sendToConfigJson = Gson().toJson(it)
                intent.putExtra("sendToConfig", sendToConfigJson)
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
