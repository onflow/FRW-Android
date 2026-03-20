package com.flowfoundation.wallet.manager.evm

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe singleton cache for COA (Cadence-Owned Account) EVM address visibility.
 *
 * Tracks which EVM addresses have been verified to have assets (FLOW balance, EVM tokens, or NFTs)
 * and should be displayed in the wallet list. Each entry has a 30-minute TTL to avoid stale data.
 *
 * Shared across DrawerLayoutViewModel and AccountListViewModel to avoid redundant API calls.
 * Cleared automatically via [com.flowfoundation.wallet.network.clearUserCache] on network/account switch.
 */
object COAVisibilityCache {

    private const val TTL_MS = 30L * 60 * 1000 // 30 minutes

    private val cache = ConcurrentHashMap<String, Long>()

    fun isVerified(evmAddress: String): Boolean {
        val timestamp = cache[evmAddress] ?: return false
        if (System.currentTimeMillis() - timestamp > TTL_MS) {
            cache.remove(evmAddress)
            return false
        }
        return true
    }

    fun markVerified(evmAddress: String) {
        cache[evmAddress] = System.currentTimeMillis()
    }

    fun markUnverified(evmAddress: String) {
        cache.remove(evmAddress)
    }

    fun clear() {
        cache.clear()
    }
}
