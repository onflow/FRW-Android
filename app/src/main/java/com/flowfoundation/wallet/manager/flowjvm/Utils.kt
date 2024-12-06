package com.flowfoundation.wallet.manager.flowjvm

import androidx.annotation.WorkerThread
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nftco.flow.sdk.Flow
import com.nftco.flow.sdk.FlowAccount
import com.nftco.flow.sdk.FlowAddress
import com.nftco.flow.sdk.FlowArgument
import com.nftco.flow.sdk.FlowArgumentsBuilder
import com.nftco.flow.sdk.FlowScriptResponse
import com.nftco.flow.sdk.cadence.Field
import com.nftco.flow.sdk.cadence.JsonCadenceBuilder
import com.nftco.flow.sdk.cadence.UFix64NumberField
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.flowjvm.model.FlowAddressListResult
import com.flowfoundation.wallet.manager.flowjvm.model.FlowBoolListResult
import com.flowfoundation.wallet.manager.flowjvm.model.FlowBoolObjResult
import com.flowfoundation.wallet.manager.flowjvm.model.FlowBoolResult
import com.flowfoundation.wallet.manager.flowjvm.model.FlowSearchAddressResult
import com.flowfoundation.wallet.manager.flowjvm.model.FlowStringBoolResult
import com.flowfoundation.wallet.manager.flowjvm.model.FlowStringListResult
import com.flowfoundation.wallet.manager.flowjvm.model.FlowStringMapResult
import com.flowfoundation.wallet.manager.flowjvm.model.FlowStringObjResult
import com.flowfoundation.wallet.manager.flowjvm.model.FlowStringResult
import com.flowfoundation.wallet.manager.flowjvm.transaction.AsArgument
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.utils.extensions.toSafeDecimal
import com.flowfoundation.wallet.utils.logd
import java.math.BigDecimal
import java.util.Locale


internal fun FlowScriptResponse.parseSearchAddress(): String? {
    // {"type":"Optional","value":{"type":"Address","value":"0x5d2cd5bf303468fa"}}
    return try {
        val result = Gson().fromJson(String(bytes), FlowSearchAddressResult::class.java)
        result.value.value
    } catch (e: Exception) {
        return null
    }
}

internal fun FlowScriptResponse.parseBool(default: Boolean = false): Boolean? {
    // {"type":"Bool","value":false}
    return try {
        val result = Gson().fromJson(String(bytes), FlowBoolResult::class.java)
        result.value
    } catch (e: Exception) {
        return default
    }
}

internal fun FlowScriptResponse.parseBoolObject(default: Boolean = false): Boolean? {
    // {"value":{"value":false,"type":"Bool"},"type":"Optional"}
    return try {
        val result = Gson().fromJson(String(bytes), FlowBoolObjResult::class.java)
        result.value.value
    } catch (e: Exception) {
        return default
    }
}

internal fun FlowScriptResponse.parseBoolList(): List<Boolean>? {
    // {"type":"Array","value":[{"type":"Bool","value":true},{"type":"Bool","value":true},{"type":"Bool","value":true},{"type":"Bool","value":true},{"type":"Bool","value":false}]}
    return try {
        val result = Gson().fromJson(String(bytes), FlowBoolListResult::class.java)
        return result.value.map { it.value }
    } catch (e: Exception) {
        null
    }
}

internal fun FlowScriptResponse.parseStringList(): List<String>? {
    return try {
        val result = Gson().fromJson(String(bytes), FlowStringListResult::class.java)
        return result.value.value.map { it.value }
    } catch (e: Exception) {
        null
    }
}

internal fun FlowScriptResponse.parseStringDecimalMap(): Map<String, BigDecimal>? {
    return try {
        val result = Gson().fromJson(String(bytes), FlowStringMapResult::class.java)
        return result.value?.associate {
            it.key?.value.toString() to it.value?.value.toSafeDecimal()
        }
    } catch (e: Exception) {
        null
    }
}

internal fun FlowScriptResponse.parseStringBoolMap(): Map<String, Boolean>? {
    return try {
        val result = Gson().fromJson(String(bytes), FlowStringBoolResult::class.java)
        return result.value?.associate {
            it.key?.value.toString() to (it.value?.value ?: false)
        }
    } catch (e: Exception) {
        null
    }
}

internal fun FlowScriptResponse.parseString(): String? {
    return try {
        val result = Gson().fromJson(String(bytes), FlowStringResult::class.java)
        result.value
    } catch (e: Exception) {
        return null
    }
}
//{"value":{"value":"A.3399d7c6c609b7e5.DAMO420.Vault","type":"String"},"type":"Optional"}
internal fun FlowScriptResponse.parseStringObject(): String? {
    return try {
        val result = Gson().fromJson(String(bytes), FlowStringObjResult::class.java)
        result.value.value
    } catch (e: Exception) {
        return null
    }
}

internal fun FlowScriptResponse?.parseFloat(default: Float = 0f): Float {
    // {"type":"UFix64","value":"12.34"}
    this ?: return default
    return try {
        val result = Gson().fromJson(String(bytes), FlowStringResult::class.java)
        (result.value.toFloatOrNull()) ?: default
    } catch (e: Exception) {
        return default
    }
}

internal fun FlowScriptResponse?.parseDecimal(default: BigDecimal = BigDecimal.ZERO): BigDecimal {
    // {"type":"UFix64","value":"12.34"}
    this ?: return default
    return try {
        val result = Gson().fromJson(String(bytes), FlowStringResult::class.java)
        result.value.toBigDecimalOrNull() ?: default
    } catch (e: Exception) {
        return default
    }
}

internal fun FlowScriptResponse?.parseAddressList(): List<String> {
    // {"value":[{"value":"0x4eaaf4f4c84dce5e","type":"Address"},{"value":"0x74dacdce5216865f","type":"Address"},{"value":"0xe424ebca3e307ef8","type":"Address"},{"value":"0x0207b0b4a27a1801","type":"Address"},{"value":"0x3da47812164a24a8","type":"Address"},{"value":"0x97e2e1f9d68910b6","type":"Address"}],"type":"Array"}
    this ?: return emptyList()
    return try {
        val result = Gson().fromJson(String(bytes), FlowAddressListResult::class.java)
        return result.value.map { it.value }
    } catch (e: Exception) {
        return emptyList()
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

fun Nft.formatCadence(cadence: Cadence): String {
    val config = NftCollectionConfig.get(collectionAddress, contractName()) ?: return cadence.getScript()
    return config.formatCadence(cadence)
}

fun NftCollection.formatCadence(cadence: Cadence): String {
    return cadence.getScript().replace("<NFT>", contractName ?: "")
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

    fun toFlowArguments(): FlowArgumentsBuilder.() -> Unit {
        return {
            _values.forEach { arg(it) }
        }
    }
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

fun JsonCadenceBuilder.ufix64Safe(number: Number): UFix64NumberField {
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
