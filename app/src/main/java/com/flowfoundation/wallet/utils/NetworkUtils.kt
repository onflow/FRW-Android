package com.flowfoundation.wallet.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

object NetworkUtils {
    
    /**
     * Check if the device has an active network connection
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    /**
     * Test DNS resolution for key indexer domains
     */
    suspend fun testKeyIndexerConnectivity(isMainnet: Boolean): Boolean = withContext(Dispatchers.IO) {
        val domain = if (isMainnet) {
            "production.key-indexer.flow.com"
        } else {
            "staging.key-indexer.flow.com"
        }
        
        return@withContext try {
            InetAddress.getByName(domain)
            true
        } catch (e: UnknownHostException) {
            logd("NetworkUtils", "DNS resolution failed for $domain: ${e.message}")
            false
        } catch (e: Exception) {
            logd("NetworkUtils", "Network test failed for $domain: ${e.message}")
            false
        }
    }
    
    /**
     * Get network type information for debugging
     */
    fun getNetworkTypeInfo(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "Unknown"
            }
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.typeName ?: "Unknown"
        }
    }
} 