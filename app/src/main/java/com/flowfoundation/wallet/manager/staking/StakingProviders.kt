package com.flowfoundation.wallet.manager.staking

import android.os.Parcelable
import android.text.format.DateUtils
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.flowfoundation.wallet.cache.stakingProviderCache
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.readTextFromAssets
import kotlinx.parcelize.Parcelize
import java.net.URL

internal class StakingProviders {
    private val providers = mutableListOf<StakingProvider>()

    fun refresh() {
        ioScope { get() }
    }

    fun get(): List<StakingProvider> {
        if (providers.isEmpty()) {
            providers.addAll(fetchCache())
        }

        fetchRemote()
        return providers.toList()
    }

    private fun fetchRemote() {
        ioScope {
            val cacheFile = stakingProviderCache()
            if (!cacheFile.isExpired(DateUtils.DAY_IN_MILLIS)) {
                return@ioScope
            }
            val text = URL("https://raw.githubusercontent.com/Outblock/Assets/main/staking/staking.json").readText()
            val list = runCatching { Gson().fromJson<List<StakingProvider>>(text, object : TypeToken<List<StakingProvider>>() {}.type) }.getOrNull()
                ?: emptyList()
            if (list.isNotEmpty()) {
                providers.clear()
                providers.addAll(list)
                cacheFile.cache(StakingProviderCache(list))
            }
        }
    }

    private fun fetchCache(): List<StakingProvider> {
        val cache = stakingProviderCache().read()?.data
        return cache ?: loadFromAssets()
    }

    private fun loadFromAssets(): List<StakingProvider> {
        val text = readTextFromAssets("config/stake_provider.json")
        return runCatching { Gson().fromJson<List<StakingProvider>>(text, object : TypeToken<List<StakingProvider>>() {}.type) }.getOrNull()
            ?: emptyList()
    }
}

@Parcelize
data class StakingProvider(
    @SerializedName("description")
    val description: String?,
    @SerializedName("icon")
    val icon: String?,
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("website")
    val website: String?,
) : Parcelable

data class StakingProviderCache(
    @SerializedName("data")
    val data: List<StakingProvider>,
)

fun StakingProvider.isLilico() = name.lowercase() == "lilico"

fun StakingProvider.rate() = if (isLilico()) StakingManager.apy() else STAKING_DEFAULT_NORMAL_APY

fun StakingProvider.delegatorId(): Int? = StakingManager.delegatorIds()[id]