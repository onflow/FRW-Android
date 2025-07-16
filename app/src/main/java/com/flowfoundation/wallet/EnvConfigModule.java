package com.flowfoundation.wallet;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

public class EnvConfigModule extends ReactContextBaseJavaModule {
    
    private static ReactApplicationContext reactContext;
    
    public EnvConfigModule(ReactApplicationContext context) {
        super(context);
        reactContext = context;
    }
    
    @Override
    public String getName() {
        return "EnvConfigModule";
    }
    
    // Static method to get environment variables from native code
    public static String getEnvVar(String key) {
        try {
            return (String) BuildConfig.class.getField(key).get(null);
        } catch (Exception e) {
            return "";
        }
    }
    
    public static boolean isDebugMode() {
        try {
            return Boolean.parseBoolean(getEnvVar("DEBUG_MODE"));
        } catch (Exception e) {
            return false;
        }
    }
    
    public static String getApiBaseUrl() {
        try {
            return getEnvVar("API_BASE_URL");
        } catch (Exception e) {
            return "";
        }
    }
    
    public static String getApiKey() {
        try {
            return getEnvVar("API_KEY");
        } catch (Exception e) {
            return "";
        }
    }
    
    public static String getEnvironment() {
        try {
            return getEnvVar("ENVIRONMENT");
        } catch (Exception e) {
            return "production";
        }
    }
    
    public static String getFlowNetwork() {
        try {
            return getEnvVar("FLOW_NETWORK");
        } catch (Exception e) {
            return "mainnet";
        }
    }
    
    public static String getFlowAccessNodeUrl() {
        try {
            return getEnvVar("FLOW_ACCESS_NODE_URL");
        } catch (Exception e) {
            return "";
        }
    }
    
    public static String getFlowDiscoveryWalletUrl() {
        try {
            return getEnvVar("FLOW_DISCOVERY_WALLET_URL");
        } catch (Exception e) {
            return "";
        }
    }
    
    @ReactMethod
    public void getAllEnvVars(Promise promise) {
        try {
            WritableMap envVars = Arguments.createMap();
            envVars.putString("API_BASE_URL", BuildConfig.API_BASE_URL);
            envVars.putString("API_KEY", BuildConfig.API_KEY);
            envVars.putBoolean("DEBUG_MODE", Boolean.parseBoolean(BuildConfig.DEBUG_MODE));
            envVars.putString("APP_VERSION", BuildConfig.APP_VERSION);
            envVars.putBoolean("ANALYTICS_ENABLED", Boolean.parseBoolean(BuildConfig.ANALYTICS_ENABLED));
            envVars.putString("ENVIRONMENT", BuildConfig.ENVIRONMENT);
            envVars.putString("FLOW_NETWORK", BuildConfig.FLOW_NETWORK);
            envVars.putString("FLOW_ACCESS_NODE_URL", BuildConfig.FLOW_ACCESS_NODE_URL);
            envVars.putString("FLOW_DISCOVERY_WALLET_URL", BuildConfig.FLOW_DISCOVERY_WALLET_URL);
            promise.resolve(envVars);
        } catch (Exception e) {
            promise.reject("ENV_ERROR", "Failed to get environment variables", e);
        }
    }
} 