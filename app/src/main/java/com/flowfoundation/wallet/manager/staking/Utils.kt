package com.flowfoundation.wallet.manager.staking

import android.text.format.DateUtils
import com.flowfoundation.wallet.utils.extensions.toSafeDouble
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.utils.extensions.toSafeInt

// 2022-10-27 07:00
const val STAKE_START_TIME = 1666825200000

fun parseStakingInfoResult(json: String?): StakingInfo? {
    json ?: return null
    val info = Gson().fromJson(json, StakingInfoInner::class.java)
    if (info.value?.value == null) {
        return null
    }
    return StakingInfo(
        nodes = info.value.value.map { value ->
            StakingNode(
                delegatorId = value.getByName("id").toSafeInt(),
                nodeID = value.getByName("nodeID").orEmpty(),
                tokensCommitted = value.getByName("tokensCommitted").toSafeDouble(),
                tokensStaked = value.getByName("tokensStaked").toSafeDouble(),
                tokensUnstaking = value.getByName("tokensUnstaking").toSafeDouble(),
                tokensRewarded = value.getByName("tokensRewarded").toSafeDouble(),
                tokensUnstaked = value.getByName("tokensUnstaked").toSafeDouble(),
                tokensRequestedToUnstake = value.getByName("tokensRequestedToUnstake").toSafeDouble(),
            )
        }
    )
}

fun stakingEpochStartTime(): Long {
    val current = System.currentTimeMillis()
    val gap = 7 * DateUtils.DAY_IN_MILLIS
    var startTime = STAKE_START_TIME
    while (startTime + gap < current) {
        startTime += gap
    }
    return startTime
}

fun stakingEpochEndTime(): Long {
    val gap = 7 * DateUtils.DAY_IN_MILLIS
    return stakingEpochStartTime() + gap
}

fun parseStakingDelegatorInfo(json: String?): Map<String, Int> {
    json ?: return emptyMap()
    val info = Gson().fromJson(json, StakingDelegatorInner::class.java)
    return info.value?.value?.associate { it?.key?.value.orEmpty() to (it?.value?.value?.firstOrNull()?.key?.value?.toSafeInt() ?: 0) }.orEmpty()
}

data class StakingInfoInner(
    @SerializedName("type")
    val type: String?,
    @SerializedName("value")
    val value: Value?
) {
    data class Value(
        @SerializedName("type")
        val type: String?,
        @SerializedName("value")
        val value: List<Value1?>?
    ) {

        data class Value1(
            @SerializedName("type")
            val type: String?,
            @SerializedName("value")
            val value: Value2?
        ) {
            data class Value2(
                @SerializedName("fields")
                val fields: List<Field?>?,
                @SerializedName("id")
                val id: String?
            ) {

                data class Field(
                    @SerializedName("name")
                    val name: String?,
                    @SerializedName("value")
                    val value: Value3?
                ) {
                    data class Value3(
                        @SerializedName("type")
                        val type: String?,
                        @SerializedName("value")
                        val value: String?
                    )
                }
            }
        }
    }
}

fun StakingInfoInner.Value.Value1?.getByName(name: String): String? = this?.value?.fields?.firstOrNull{ it?.name == name }?.value?.value

internal data class StakingDelegatorInner(
    @SerializedName("type")
    val type: String?,
    @SerializedName("value")
    val value: Value1?
) {
    internal data class Value1(
        @SerializedName("type")
        val type: String?,
        @SerializedName("value")
        val value: List<Value2?>?
    ) {
        internal data class Value2(
            @SerializedName("key")
            val key: Key?,
            @SerializedName("value")
            val value: Value?
        ) {
            internal data class Key(
                @SerializedName("type")
                val type: String?,
                @SerializedName("value")
                val value: String?
            )

            internal data class Value(
                @SerializedName("type")
                val type: String?,
                @SerializedName("value")
                val value: List<Value3?>?
            ) {
                internal data class Value3(
                    @SerializedName("key")
                    val key: Key?
                ) {
                    internal data class Key(
                        @SerializedName("type")
                        val type: String?,
                        @SerializedName("value")
                        val value: String?
                    )
                }
            }
        }
    }
}
