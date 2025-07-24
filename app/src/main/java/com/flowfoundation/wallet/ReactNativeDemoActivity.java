package com.flowfoundation.wallet;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.ReactActivity;
import com.facebook.react.ReactActivityDelegate;
import com.facebook.react.defaults.DefaultReactActivityDelegate;
import com.flowfoundation.wallet.bridge.QRCodeScanManager;

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
                Bundle initialProps = new Bundle();
                
                Intent intent = getIntent();
                if (intent != null) {
                    String address = intent.getStringExtra("address");
                    String network = intent.getStringExtra("network");
                    String initialRoute = intent.getStringExtra("initialRoute");
                    
                    if (address != null) {
                        initialProps.putString("address", address);
                        Log.d(TAG, "Added address to launch options: " + address);
                    }
                    if (network != null) {
                        initialProps.putString("network", network);
                        Log.d(TAG, "Added network to launch options: " + network);
                    }
                    if (initialRoute != null) {
                        initialProps.putString("initialRoute", initialRoute);
                        Log.d(TAG, "Added initialRoute to launch options: " + initialRoute);
                    }
                }
                
                Log.d(TAG, "Launch options created with " + initialProps.size() + " properties");
                return initialProps;
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
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Handle QR scan result
        QRCodeScanManager.INSTANCE.handleScanResult(resultCode, data);
    }

    /**
     * Launch the React Native Demo Activity
     */
    public static void launch(Context context) {
        launch(context, null, null, null);
    }

    /**
     * Launch the React Native Demo Activity with parameters
     */
    public static void launch(Context context, String screenName, String address, String network) {
        Log.d(TAG, "Launching ReactNativeDemoActivity with params:");
        Log.d(TAG, "  screenName: " + screenName);
        Log.d(TAG, "  address: " + address);
        Log.d(TAG, "  network: " + network);
        
        Intent intent = new Intent(context, ReactNativeDemoActivity.class);
        if (address != null) {
            intent.putExtra("address", address);
        }
        if (network != null) {
            intent.putExtra("network", network);
        }
        if (screenName != null) {
            intent.putExtra("initialRoute", screenName);
        }
        context.startActivity(intent);
    }
}