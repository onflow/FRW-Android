package com.flowfoundation.wallet.manager.staking

import androidx.annotation.WorkerThread
import com.flowfoundation.wallet.cache.stakingCache
import com.flowfoundation.wallet.manager.flowjvm.CadenceScript
import com.flowfoundation.wallet.manager.flowjvm.executeCadence
import com.flowfoundation.wallet.manager.flowjvm.transactionByMainWallet
import com.flowfoundation.wallet.manager.flowjvm.ufix64Safe
import com.flowfoundation.wallet.manager.transaction.TransactionStateWatcher
import com.flowfoundation.wallet.manager.transaction.isExecuteFinished
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.loge
import com.flowfoundation.wallet.utils.logv
import com.flowfoundation.wallet.utils.uiScope
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.runBlocking
import org.onflow.flow.infrastructure.Cadence
import java.lang.ref.WeakReference
import java.math.BigDecimal
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

    fun stakingNode(provider: StakingProvider) =
        stakingInfo().nodes.firstOrNull { it.nodeID == provider.id }

    fun providers() = providers.get()

    fun delegatorIds() = delegatorIds.toMap()

    fun hasBeenSetup() = isSetup

    fun apy() = apy

    fun isStaked(): Boolean {
        return stakingCount() > 0.0f
    }

    fun stakingCount() = stakingInfo.nodes.sumOf { it.tokensCommitted + it.tokensStaked }.toFloat()

    fun isStaking(): Boolean {
        val count = stakingInfo.nodes.sumOf {
            it.tokensCommitted + it.tokensStaked + it
                .tokensRewarded + it.tokensRequestedToUnstake + it.tokensUnstaking + it.tokensUnstaked
        }
            .toFloat()
        return count > 0.0f
    }

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

    private suspend fun updateApy() {
        queryStakingApy(CadenceScript.CADENCE_GET_STAKE_APY_BY_WEEK)?.let {
            apy = it
            cache()
        }

        queryStakingApy(CadenceScript.CADENCE_GET_STAKE_APY_BY_YEAR)?.let {
            apyYear = it
            cache()
        }
    }


    private fun cache() {
        ioScope {
            stakingCache().cache(StakingCache(info = stakingInfo, apy = apy, isSetup = isSetup))
        }
    }

    private suspend fun queryStakingInfo(): StakingInfo? {
        val address = WalletManager.wallet()?.walletAddress() ?: return null

        logv(TAG, "queryStakingInfo ")
        return runCatching {
            val response = CadenceScript.CADENCE_QUERY_STAKE_INFO.executeCadence {
                arg { Cadence.address(address) }
            }
            val text = response?.encode()
            logv(TAG, "queryStakingInfo response:$text")
            parseStakingInfoResult(text)
        }.onFailure {
            println(it)
            loge(TAG, "queryStakingInfo failure:$it")
        }.getOrNull()
    }

    private fun dispatchListener() {
        uiScope {
            listeners.forEach { it.get()?.onStakingInfoUpdate() }
            listeners.removeAll { it.get() == null }
        }
    }

    fun clear() {
        stakingCache().clear()
        stakingInfo = StakingInfo()
    }

}

private suspend fun queryStakingApy(cadenceScript: CadenceScript): Float? {
    return runCatching {
        val response = cadenceScript.executeCadence {}
        val apy = response?.decode<Float>()
        logd(TAG, "queryStakingApy apy:$apy")
        if (apy == 0.0f) null else apy
    }.getOrNull()
}

suspend fun createStakingDelegatorId(provider: StakingProvider, amount: BigDecimal) =
    suspendCoroutine { continuation ->
        runCatching {
            runBlocking {
                logd(TAG, "createStakingDelegatorId providerId：${provider.id}")
                val txId = CadenceScript.CADENCE_CREATE_STAKE_DELEGATOR_ID.transactionByMainWallet {
                    arg { string(provider.id) }
                    arg { ufix64Safe(amount) }
                }
                logd(TAG, "createStakingDelegatorId txId：$txId")
                TransactionStateWatcher(txId!!).watch { result ->
                    if (result.isExecuteFinished()) {
                        MixpanelManager.delegationCreated(
                            WalletManager.wallet()?.walletAddress().orEmpty(),
                            provider.id,
                            amount.toString()
                        )
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
        val txId = CadenceScript.CADENCE_SETUP_STAKING.transactionByMainWallet {} ?: return
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
        ioScope {
            val address = WalletManager.selectedWalletAddress()
            val response = CadenceScript.CADENCE_GET_DELEGATOR_INFO.executeCadence {
                arg { Cadence.address(address) }
            }?.encode()
            logv(TAG, "getDelegatorInfo response:${response}")
            continuation.resume(parseStakingDelegatorInfo(response))
        }
    }.getOrElse { continuation.resume(mapOf<String, Int>()) }
}

@WorkerThread
private suspend fun checkHasBeenSetup(): Boolean {
    return runCatching {
        val address = WalletManager.selectedWalletAddress()
        val response =
            CadenceScript.CADENCE_CHECK_IS_STAKING_SETUP.executeCadence { arg { Cadence.address(address) } }
        response?.decode<Boolean>() ?: false
    }.getOrElse { false }
}

interface StakingInfoUpdateListener {
    fun onStakingInfoUpdate()
}

data class StakingInfo(
    @SerializedName("nodes")
    val nodes: List<StakingNode> = emptyList(),
)

data class StakingNode(
    @SerializedName("delegatorId")
    val delegatorId: Int? = null,
    @SerializedName("nodeID")
    val nodeID: String = "",
    @SerializedName("tokensCommitted")
    val tokensCommitted: Double = 0.0,
    @SerializedName("tokensStaked")
    val tokensStaked: Double = 0.0,
    @SerializedName("tokensUnstaking")
    val tokensUnstaking: Double = 0.0,
    @SerializedName("tokensRewarded")
    val tokensRewarded: Double = 0.0,
    @SerializedName("tokensUnstaked")
    val tokensUnstaked: Double = 0.0,
    @SerializedName("tokensRequestedToUnstake")
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