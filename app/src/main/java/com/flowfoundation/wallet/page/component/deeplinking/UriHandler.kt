package com.flowfoundation.wallet.page.component.deeplinking

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.walletconnect.WalletConnect
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.toast
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val TAG = "UriHandler"

/**
 * Central utility class to handle different URI types (deep links, universal links)
 */
object UriHandler {

    /**
     * Process any URI to determine what type it is and how it should be handled
     */
    fun processUri(context: Context, uri: Uri): Boolean {
        logd(TAG, "Processing URI: $uri")
        
        // First check if it's a Telegram scheme
        if (handleTelegramUri(context, uri)) {
            return true
        }
        
        // Check for WalletConnect URIs
        val wcUri = extractWalletConnectUri(uri)
        if (wcUri != null && wcUri.startsWith("wc:")) {
            return handleWalletConnectUri(context, wcUri)
        }
        
        // Handle other deep links and universal links
        return when {
            uri.scheme == "http" || uri.scheme == "https" -> {
                // Check if it's a known universal link host
                if (UniversalLinkHost.isKnownHost(uri.host)) {
                    handleUniversalLink(context, uri)
                } else {
                    false
                }
            }
            DeepLinkScheme.isKnownScheme(uri.scheme) -> {
                handleDeepLink(context, uri)
            }
            else -> false
        }
    }
    
    /**
     * Handle Telegram deep links
     */
    private fun handleTelegramUri(context: Context, uri: Uri): Boolean {
        if (uri.scheme == DeepLinkScheme.TG.scheme) {
            logd(TAG, "Handling Telegram URI: $uri")
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                logd(TAG, "Telegram app is not installed: ${e.message}")
                toast(msgRes = R.string.telegram_not_installed)
            }
            return true
        }
        return false
    }
    
    /**
     * Handle WalletConnect URIs
     */
    private fun handleWalletConnectUri(context: Context, wcUri: String): Boolean {
        logd(TAG, "Handling WalletConnect URI: $wcUri")
        return try {
            if (WalletConnect.isInitialized()) {
                WalletConnect.get().pair(wcUri)
                true
            } else {
                logd(TAG, "WalletConnect not initialized")
                // Save for later processing or initialize
                // PendingActionHelper.savePendingDeepLink(context, Uri.parse(wcUri))
                false
            }
        } catch (e: Exception) {
            loge(TAG, "Error handling WalletConnect URI: ${e.message}")
            loge(e)
            false
        }
    }
    
    /**
     * Handle universal links (HTTP/HTTPS)
     */
    private fun handleUniversalLink(context: Context, uri: Uri): Boolean {
        val host = UniversalLinkHost.fromHost(uri.host) ?: return false
        logd(TAG, "Handling Universal Link for host: $host")
        
        return when (host) {
            UniversalLinkHost.LILICO, 
            UniversalLinkHost.FRW_LINK, 
            UniversalLinkHost.FCW_LINK, 
            UniversalLinkHost.WALLET_LINK -> {
                // Process based on path
                processWalletLinkPaths(context, uri)
            }
            UniversalLinkHost.WC -> {
                // WalletConnect host
                val wcUri = extractWalletConnectUri(uri)
                if (wcUri != null) {
                    handleWalletConnectUri(context, wcUri)
                } else {
                    false
                }
            }
        }
    }
    
    /**
     * Handle deep links (custom schemes)
     */
    private fun handleDeepLink(context: Context, uri: Uri): Boolean {
        val scheme = DeepLinkScheme.fromScheme(uri.scheme) ?: return false
        logd(TAG, "Handling Deep Link for scheme: $scheme")
        
        return when (scheme) {
            DeepLinkScheme.WC -> {
                // Direct WalletConnect URI
                handleWalletConnectUri(context, uri.toString())
            }
            DeepLinkScheme.FW,
            DeepLinkScheme.FRW,
            DeepLinkScheme.FCW,
            DeepLinkScheme.LILICO -> {
                // Custom wallet schemes
                // Process based on path
                processWalletLinkPaths(context, uri)
            }
            DeepLinkScheme.TG -> {
                // Already handled in processUri
                false
            }
        }
    }
    
    /**
     * Process wallet link paths for actions like send, dapp, etc.
     */
    private fun processWalletLinkPaths(context: Context, uri: Uri): Boolean {
        // Handle paths like /send, /dapp, etc.
        // Will be implemented based on your existing DeepLinkPath handling
        // For now, just save the pending action
        PendingActionHelper.savePendingDeepLink(context, uri)
        return true
    }
    
    /**
     * Extract WalletConnect URI from various formats
     */
    fun extractWalletConnectUri(uri: Uri): String? {
        return try {
            // Skip processing if this is a Telegram URI
            if (uri.scheme == DeepLinkScheme.TG.scheme) {
                return null
            }
            
            val uriString = uri.toString()
            
            // Direct WC URI
            if (uriString.startsWith("wc:")) {
                return uriString
            }
            
            val uriParamStart = uriString.indexOf("uri=")
            val wcUriEncoded = if (uriParamStart != -1) {
                uriString.substring(uriParamStart + 4)
            } else {
                uri.getQueryParameter("uri")
            }
            
            wcUriEncoded?.let {
                if (it.contains("%")) {
                    URLDecoder.decode(it, StandardCharsets.UTF_8.name())
                } else {
                    it
                }
            }
        } catch (e: Exception) {
            loge(TAG, "Error extracting WalletConnect URI: ${e.message}")
            loge(e)
            null
        }
    }
} 