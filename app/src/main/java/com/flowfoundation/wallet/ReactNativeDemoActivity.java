package com.flowfoundation.wallet;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.ReactActivity;
import com.facebook.react.ReactActivityDelegate;
import com.facebook.react.defaults.DefaultReactActivityDelegate;
import com.flowfoundation.wallet.bridge.RNBridge;
import com.flowfoundation.wallet.manager.wallet.WalletManager;
import com.flowfoundation.wallet.manager.app.ChainNetworkKt;
import com.flowfoundation.wallet.wallet.WalletUtilsKt;
import com.google.gson.Gson;
import java.util.List;

public class ReactNativeDemoActivity extends ReactActivity {

    private static final String TAG = "ReactNativeDemoActivity";

    /**
     * Returns the name of the main component registered from JavaScript. This is used to schedule
     * rendering of the component.
     */
    @Override
    protected String getMainComponentName() {
        return "FRWRN";
    }

    /**
     * Returns the instance of the {@link ReactActivityDelegate}. Here we use a util class {@link
     * DefaultReactActivityDelegate} which allows you to easily enable Fabric and Concurrent React
     * (aka React 18) with two boolean flags.
     */
    @Override
    protected ReactActivityDelegate createReactActivityDelegate() {
        return new DefaultReactActivityDelegate(
            this,
            getMainComponentName(),
            false, // fabricEnabled
            false  // concurrentRootEnabled
        ) {
            @Override
            protected Bundle getLaunchOptions() {
                Bundle launchOptions = new Bundle();

                Intent intent = getIntent();
                if (intent != null) {
                    String address = intent.getStringExtra("address");
                    String network = intent.getStringExtra("network");
                    String initialRoute = intent.getStringExtra("initialRoute");
                    String screen = intent.getStringExtra("screen");
                    String sendToConfigJson = intent.getStringExtra("sendToConfig");

                    // Top level props
                    if (address != null) {
                        launchOptions.putString("address", address);
                        Log.d(TAG, "Added address to launch options: " + address);
                    }
                    if (network != null) {
                        launchOptions.putString("network", network);
                        Log.d(TAG, "Added network to launch options: " + network);
                    }
                    if (initialRoute != null) {
                        launchOptions.putString("initialRoute", initialRoute);
                        Log.d(TAG, "Added initialRoute to launch options: " + initialRoute);
                    }

                    // Create initialProps object if we have screen or sendToConfig
                    if (screen != null || sendToConfigJson != null) {
                        Bundle initialPropsBundle = new Bundle();

                        if (screen != null) {
                            initialPropsBundle.putString("screen", screen);
                            Log.d(TAG, "Added screen to initialProps: " + screen);
                        }
                        if (sendToConfigJson != null) {
                            initialPropsBundle.putString("sendToConfig", sendToConfigJson);
                            Log.d(TAG, "Added sendToConfig to initialProps: " + sendToConfigJson);
                        }

                        launchOptions.putBundle("initialProps", initialPropsBundle);
                        Log.d(TAG, "Added initialProps bundle with " + initialPropsBundle.size() + " properties");
                    }
                }

                Log.d(TAG, "Launch options created with " + launchOptions.size() + " properties");
                return launchOptions;
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate called");
        super.onCreate(savedInstanceState);

        // Log the intent extras for debugging
        Intent intent = getIntent();
        if (intent != null) {
            Log.d(TAG, "Intent extras:");
            Log.d(TAG, "  address: " + intent.getStringExtra("address"));
            Log.d(TAG, "  network: " + intent.getStringExtra("network"));
            Log.d(TAG, "  initialRoute: " + intent.getStringExtra("initialRoute"));
            Log.d(TAG, "  screen: " + intent.getStringExtra("screen"));
            Log.d(TAG, "  sendToConfig: " + intent.getStringExtra("sendToConfig"));
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Handle QR scan result
//        QRCodeScanManager.INSTANCE.handleScanResult(resultCode, data);
    }

    /**
     * Launch the React Native Demo Activity
     */
    public static void launch(Context context) {
        launch(context, null, null, null);
    }

    /**
     * Launch the React Native Demo Activity with default address and network
     */
    public static void launch(Context context, RNBridge.ScreenType screenType) {
        String address = WalletUtilsKt.toAddress(WalletManager.INSTANCE.selectedWalletAddress());
        String network = ChainNetworkKt.chainNetWorkString();
        launch(context, screenType, address, network);
    }

    /**
     * Launch the React Native Demo Activity with parameters
     */
    public static void launch(Context context, RNBridge.ScreenType screenType, String address, String network) {
        Log.d(TAG, "Launching ReactNativeDemoActivity with params:");
        Log.d(TAG, "  screenType: " + screenType);
        Log.d(TAG, "  address: " + address);
        Log.d(TAG, "  network: " + network);

        Intent intent = new Intent(context, ReactNativeDemoActivity.class);

        // Add flags to ensure the activity comes to the foreground prominently
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        if (address != null) {
            intent.putExtra("address", address);
        }
        if (network != null) {
            intent.putExtra("network", network);
        }
        if (screenType != null) {
            // Convert screen enum to string and set both screen and initialRoute
            String screenString = screenType == RNBridge.ScreenType.SEND_ASSET ? "send-asset" : "token-detail";
            String routeName = screenType == RNBridge.ScreenType.SEND_ASSET ? "SelectTokens" : "Home";

            intent.putExtra("screen", screenString);
            intent.putExtra("initialRoute", routeName);
        }
        context.startActivity(intent);
    }

    /**
     * Launch with InitialProps containing screen and SendToConfig
     */
    public static void launchWithConfig(Context context, RNBridge.ScreenType screenType, RNBridge.SendToConfig sendToConfig, String address, String network) {
        Log.d(TAG, "Launching ReactNativeDemoActivity with config:");
        Log.d(TAG, "  screenType: " + screenType);
        Log.d(TAG, "  address: " + address);
        Log.d(TAG, "  network: " + network);

        Intent intent = new Intent(context, ReactNativeDemoActivity.class);

        // Add flags to ensure the activity comes to the foreground prominently
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        if (address != null) {
            intent.putExtra("address", address);
        }
        if (network != null) {
            intent.putExtra("network", network);
        }

        // Convert screen enum to string and set both screen and initialRoute
        String screenString = screenType == RNBridge.ScreenType.SEND_ASSET ? "send-asset" : "token-detail";
        String routeName = screenType == RNBridge.ScreenType.SEND_ASSET ? "SelectTokens" : "Home";

        intent.putExtra("screen", screenString);
        intent.putExtra("initialRoute", routeName);

        // Serialize SendToConfig to JSON
        if (sendToConfig != null) {
            String sendToConfigJson = new Gson().toJson(sendToConfig);
            intent.putExtra("sendToConfig", sendToConfigJson);
            Log.d(TAG, "  sendToConfig JSON: " + sendToConfigJson);
        }

        context.startActivity(intent);
    }

    /**
     * Convenience method for token send with default address and network
     */
    public static void launchTokenSend(Context context, RNBridge.TokenModel token) {
        String address = WalletUtilsKt.toAddress(WalletManager.INSTANCE.selectedWalletAddress());
        String network = ChainNetworkKt.chainNetWorkString();
        RNBridge.SendToConfig sendToConfig = new RNBridge.SendToConfig(token, null, null, null);
        launchWithConfig(context, RNBridge.ScreenType.SEND_ASSET, sendToConfig, address, network);
    }

    /**
     * Convenience method for NFT send with default address and network
     */
    public static void launchNFTSend(Context context, List<RNBridge.NFTModel> nfts) {
        String address = WalletUtilsKt.toAddress(WalletManager.INSTANCE.selectedWalletAddress());
        String network = ChainNetworkKt.chainNetWorkString();
        RNBridge.SendToConfig sendToConfig = new RNBridge.SendToConfig(null, null, nfts, null);
        launchWithConfig(context, RNBridge.ScreenType.SEND_ASSET, sendToConfig, address, network);
    }

    /**
     * Convenience method for Wallet send with default address and network
     * @param fromAccount can be null - if null, RN will handle account selection
     * @param targetAddress can be null - if null, user will select target in RN
     */
    public static void launchWalletSend(Context context, RNBridge.WalletAccount fromAccount, String targetAddress) {
        String address = WalletUtilsKt.toAddress(WalletManager.INSTANCE.selectedWalletAddress());
        String network = ChainNetworkKt.chainNetWorkString();
        RNBridge.SendToConfig sendToConfig = new RNBridge.SendToConfig(null, fromAccount, null, targetAddress);
        launchWithConfig(context, RNBridge.ScreenType.SEND_ASSET, sendToConfig, address, network);
    }
}
