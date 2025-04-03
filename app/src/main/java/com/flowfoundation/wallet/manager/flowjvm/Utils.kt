package com.flowfoundation.wallet.manager.flowjvm

import androidx.annotation.WorkerThread
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nftco.flow.sdk.Flow
import com.nftco.flow.sdk.FlowAccount
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.FlowArgument
import com.nftco.flow.sdk.cadence.Field
import com.nftco.flow.sdk.cadence.JsonCadenceBuilder
import com.nftco.flow.sdk.cadence.UFix64NumberField
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.flowjvm.transaction.AsArgument
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.utils.extensions.toSafeDecimal
import com.flowfoundation.wallet.utils.logd
import org.onflow.flow.infrastructure.Cadence
import java.math.BigDecimal
import java.util.Locale

internal fun Cadence.Value?.parseBigDecimal(default: BigDecimal = BigDecimal.ZERO): BigDecimal {
    this ?: return default
    return try {
        this.decode<String>().toBigDecimalOrNull() ?: default
    } catch (e: Exception) {
        return default
    }
}

internal fun Map<String, String>?.parseBigDecimalMap(): Map<String, BigDecimal>? {
    return try {
        this?.mapValues { (_, value) ->
            value.toSafeDecimal()
        }
    } catch (e: Exception) {
        return null
    }
}

fun addressVerify(address: String): Boolean {
    if (!address.startsWith("0x")) {
        return false
    }
    return try {
        FlowApi.get().getAccountAtLatestBlock(FlowAddress(address)) != null
    } catch (e: Exception) {
        false
    }
}

fun Nft.formatCadence(cadenceScript: CadenceScript): String {
    val config = NftCollectionConfig.get(collectionAddress, contractName()) ?: return cadenceScript.getScript()
    return config.formatCadence(cadenceScript)
}

fun NftCollection.formatCadence(cadenceScript: CadenceScript): String {
    return cadenceScript.getScript().replace("<NFT>", contractName ?: "")
        .replace("<NFTAddress>", address ?: "")
        .replace("<CollectionStoragePath>", path?.storagePath ?: "")
        .replace("<CollectionPublic>", path?.publicCollectionName ?: "")
        .replace("<CollectionPublicPath>", path?.publicPath ?: "")
        .replace("<Token>", contractName ?: "")
        .replace("<TokenAddress>", address ?: "")
        .replace("<TokenCollectionStoragePath>", path?.storagePath ?: "")
        .replace("<TokenCollectionPublic>", path?.publicCollectionName ?: "")
        .replace("<TokenCollectionPublicPath>", path?.publicPath ?: "")
        .replace("<CollectionPublicType>", path?.publicType ?: "")
        .replace("<CollectionPrivateType>", path?.privateType ?: "")
}

class CadenceArgumentsBuilder {
    private var _values: MutableList<Field<*>> = mutableListOf()

    fun arg(arg: Field<*>) = _values.add(arg)

    fun arg(builder: JsonCadenceBuilder.() -> Field<*>) = arg(builder(JsonCadenceBuilder()))

    fun build(): MutableList<Field<*>> = _values
}

fun (CadenceArgumentsBuilder.() -> Unit).builder(): CadenceArgumentsBuilder {
    val argsBuilder = CadenceArgumentsBuilder()
    this(argsBuilder)
    return argsBuilder
}

@WorkerThread
fun FlowAddress.lastBlockAccount(): FlowAccount? {
    return FlowApi.get().getAccountAtLatestBlock(this)
}

@WorkerThread
fun FlowAddress.lastBlockAccountKeyId(): Int {
    return lastBlockAccount()?.keys?.firstOrNull()?.id ?: 0
}

@WorkerThread
fun FlowAddress.currentKeyId(publicKey: String): Int {
    return lastBlockAccount()?.keys?.firstOrNull { publicKey == it.publicKey.base16Value }?.id ?: 0
}

fun Field<*>.valueString(): String = if (value is String) value as String else Flow.OBJECT_MAPPER.writeValueAsString(value)

fun FlowArgument.toAsArgument(): AsArgument {
    with(jsonCadence) {
        return AsArgument(
            type = type,
            value = when (value) {
                is Array<*> -> (value as Array<*>).map { (it as? Field<*>)?.toObj() ?: it.toString() }
                is String -> value as String
                else -> valueToObj()
            },
        )
    }
}

fun ufix64Safe(number: Number): UFix64NumberField {
    logd("xxx", "number:$number")
    val value = if (number is Long || number is Int) {
        number.toFloat()
    } else number
    return UFix64NumberField("%.8f".format(Locale.US, value))
}

private fun Field<*>.toObj(): Any {
    if (value is String) return mapOf("type" to type, "value" to value as String)

    val json = Flow.OBJECT_MAPPER.writeValueAsString(value)
    return runCatching {
        Gson().fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)
    }.getOrNull() ?: json
}

private fun Field<*>.valueToObj(): Any {
    val json = Flow.OBJECT_MAPPER.writeValueAsString(value)
    return runCatching {
        Gson().fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)
    }.getOrNull() ?: json
}
