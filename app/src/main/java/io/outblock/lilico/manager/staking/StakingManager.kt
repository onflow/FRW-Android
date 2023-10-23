package io.outblock.lilico.manager.staking

import androidx.annotation.WorkerThread
import com.google.gson.annotations.SerializedName
import com.nftco.flow.sdk.decode
import io.outblock.lilico.cache.stakingCache
import io.outblock.lilico.manager.flowjvm.*
import io.outblock.lilico.manager.transaction.TransactionStateWatcher
import io.outblock.lilico.manager.transaction.isExecuteFinished
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.logd
import io.outblock.lilico.utils.logv
import io.outblock.lilico.utils.uiScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val DEFAULT_APY = 0.093f
const val STAKING_DEFAULT_NORMAL_APY = 0.08f
private const val TAG = "StakingManager"

object StakingManager {

    private var stakingInfo = StakingInfo()
    private var apy = DEFAULT_APY
    private var apyYear = DEFAULT_APY
    private var isSetup = false

    private var rawStakingInfo: List<RawStakingNode>? = null

    private val providers = StakingProviders().apply { refresh() }

    private val delegatorIds = ConcurrentHashMap<String, Int>()

    private val listeners = mutableListOf<WeakReference<StakingInfoUpdateListener>>()

    fun init() {
        ioScope {
            val cache = stakingCache().read()
            stakingInfo = cache?.info ?: StakingInfo()
            apy = cache?.apy ?: apy
            apyYear = cache?.apyYear ?: apyYear
            isSetup = cache?.isSetup ?: isSetup
        }
    }

    fun addStakingInfoUpdateListener(listener: StakingInfoUpdateListener) {
        listeners.add(WeakReference(listener))
    }

    fun stakingInfo() = stakingInfo

    fun rawStakingInfo() = rawStakingInfo

    fun stakingNode(provider: StakingProvider) = stakingInfo().nodes.firstOrNull { it.nodeID == provider.id }

    fun providers() = providers.get()

    fun delegatorIds() = delegatorIds.toMap()

    fun hasBeenSetup() = isSetup

    fun apy() = apy

    fun apyYear() = apyYear

    fun isStaked(): Boolean {
        if (stakingInfo.nodes.isEmpty()) {
            refresh()
        }
        return stakingCount() > 0.0f
    }

    fun stakingCount() = stakingInfo.nodes.sumOf { it.tokensCommitted.toDouble() + it.tokensStaked }.toFloat()

    fun refresh() {
        ioScope {
            updateApy()
            isSetup = checkHasBeenSetup()
            stakingInfo = queryStakingInfo() ?: stakingInfo
            refreshDelegatorInfo()
            cache()
            dispatchListener()
        }
    }

    suspend fun setup() = suspendCoroutine { continuation ->
        runBlocking {
            runCatching {
                setupStaking {
                    continuation.resume(true)
                    refresh()
                }
            }.getOrElse { continuation.resume(false) }
        }
    }

    suspend fun refreshDelegatorInfo() {
        val ids = getDelegatorInfo()
        if (ids.isNotEmpty()) {
            delegatorIds.clear()
            delegatorIds.putAll(ids)
            logd(TAG, "delegatorIds:$delegatorIds")
        }
    }

    private fun updateApy() {
        queryStakingApy(CADENCE_GET_STAKE_APY_BY_WEEK)?.let {
            apy = it
            cache()
        }

        queryStakingApy(CADENCE_GET_STAKE_APY_BY_YEAR)?.let {
            apyYear = it
            cache()
        }
    }


    private fun cache() {
        ioScope {
            stakingCache().cache(StakingCache(info = stakingInfo, apy = apy, isSetup = isSetup))
        }
    }

    private fun queryStakingInfo(): StakingInfo? {
        val address = WalletManager.selectedWalletAddress()

        return runCatching {
            val response = CADENCE_QUERY_STAKE_INFO.executeCadence {
                arg { address(address) }
            }
            rawStakingInfo =  response?.decode<List<RawStakingNode>>() ?: emptyList()
            val text = String(response!!.bytes)
            logv(TAG, "queryStakingInfo response:$text")
            parseStakingInfoResult(text)
        }.onFailure {
            println(it)
        }.getOrNull()
    }

    private fun dispatchListener() {
        uiScope {
            listeners.forEach { it.get()?.onStakingInfoUpdate() }
            listeners.removeAll { it.get() == null }
        }
    }

}

private fun queryStakingApy(cadence: String): Float? {
    return runCatching {
        val response = cadence.executeCadence {}
        val apy = response?.parseFloat()
        logd(TAG, "queryStakingApy apy:$apy")
        if (apy == 0.0f) null else apy
    }.getOrNull()
}

suspend fun createStakingDelegatorId(provider: StakingProvider, amount: Double) = suspendCoroutine { continuation ->
    runCatching {
        runBlocking {
            logd(TAG, "createStakingDelegatorId providerId：${provider.id}")
            val txId = CADENCE_CREATE_STAKE_DELEGATOR_ID.transactionByMainWallet {
                arg { string(provider.id) }
                arg { ufix64Safe(amount) }
            }
            logd(TAG, "createStakingDelegatorId txId：$txId")
            TransactionStateWatcher(txId!!).watch { result ->
                if (result.isExecuteFinished()) {
                    continuation.resume(true)
                }
            }
        }
    }.getOrElse { continuation.resume(false) }
}

private suspend fun setupStaking(callback: () -> Unit) {
    logd(TAG, "setupStaking start")
    runCatching {
        if (checkHasBeenSetup()) {
            callback.invoke()
            return
        }
        val txId = CADENCE_SETUP_STAKING.transactionByMainWallet {} ?: return
        logd(TAG, "setupStaking txId:$txId")
        TransactionStateWatcher(txId).watch { result ->
            if (result.isExecuteFinished()) {
                logd(TAG, "setupStaking finish")
                callback.invoke()
            }
        }
    }.getOrElse { callback.invoke() }
}

private suspend fun getDelegatorInfo() = suspendCoroutine { continuation ->
    logd(TAG, "getDelegatorInfo start")
    runCatching {
        val address = WalletManager.selectedWalletAddress()
        val response = CADENCE_GET_DELEGATOR_INFO.executeCadence {
            arg { address(address) }
        }!!
        logv(TAG, "getDelegatorInfo response:${String(response.bytes)}")
        continuation.resume(parseStakingDelegatorInfo(String(response.bytes)))
    }.getOrElse { continuation.resume(mapOf<String, Int>()) }
}

@WorkerThread
private fun checkHasBeenSetup(): Boolean {
    return runCatching {
        val address = WalletManager.selectedWalletAddress()
        val response = CADENCE_CHECK_IS_STAKING_SETUP.executeCadence { arg { address(address) } }
        response?.parseBool(false) ?: false
    }.getOrElse { false }
}

interface StakingInfoUpdateListener {
    fun onStakingInfoUpdate()
}

data class StakingInfo(
    val nodes: List<StakingNode> = emptyList(),
)

data class StakingNode(
    @SerializedName("delegatorId")
    val delegatorId: Int? = null,
    @SerializedName("nodeID")
    val nodeID: String = "",
    @SerializedName("tokensCommitted")
    val tokensCommitted: Float = 0.0f,
    @SerializedName("tokensStaked")
    val tokensStaked: Float = 0.0f,
    @SerializedName("tokensUnstaking")
    val tokensUnstaking: Float = 0.0f,
    @SerializedName("tokensRewarded")
    val tokensRewarded: Float = 0.0f,
    @SerializedName("tokensUnstaked")
    val tokensUnstaked: Float = 0.0f,
    @SerializedName("tokensRequestedToUnstake")
    val tokensRequestedToUnstake: Float = 0.0f,
)

@Serializable
data class RawStakingNode(
    val id: Int? = null,
    val nodeID: String = "",
    val tokensCommitted: Double = 0.0,
    val tokensStaked: Double = 0.0,
    val tokensUnstaking: Double = 0.0,
    val tokensRewarded: Double = 0.0,
    val tokensUnstaked: Double = 0.0,
    val tokensRequestedToUnstake: Double = 0.0,
)

data class StakingCache(
    @SerializedName("info")
    val info: StakingInfo? = null,
    @SerializedName("apy")
    val apy: Float = DEFAULT_APY,
    @SerializedName("apyYear")
    val apyYear: Float = DEFAULT_APY,
    @SerializedName("isSetup")
    val isSetup: Boolean = false,
)

fun StakingNode.stakingCount() = tokensCommitted + tokensStaked

fun StakingNode.isLilico() = StakingManager.providers().firstOrNull { it.isLilico() }?.id == nodeID