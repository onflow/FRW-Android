package com.flowfoundation.wallet.manager.flowjvm

import androidx.annotation.WorkerThread
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.manager.config.NftCollectionConfig
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.utils.extensions.toSafeDecimal
import com.flowfoundation.wallet.utils.logd
import com.ionspin.kotlin.bignum.integer.BigInteger
import org.onflow.flow.infrastructure.Cadence
import org.onflow.flow.models.Account
import org.onflow.flow.models.FlowAddress
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
    return address.startsWith("0x")
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
    private var _values: MutableList<Cadence.Value> = mutableListOf()

    fun arg(arg: Cadence.Value) = _values.add(arg)

    fun arg(builder: CadenceArgumentsBuilder.() -> Cadence.Value) = arg(builder(this))

    fun build(): MutableList<Cadence.Value> = _values
    
    // Helper functions to create Flow KMM Cadence types
    fun string(value: String): Cadence.Value = Cadence.string(value)
    
    fun address(value: String): Cadence.Value {
        val cleanAddress = if (value.startsWith("0x")) value.substring(2) else value
        return Cadence.address(cleanAddress)
    }
    
    fun ufix64(value: Double): Cadence.Value = Cadence.ufix64(value)
    
    fun uint64(value: String): Cadence.Value = Cadence.uint64(value.toULong())
    fun uint64(value: Long): Cadence.Value = Cadence.uint64(value.toULong())
    fun uint64(value: Int): Cadence.Value = Cadence.uint64(value.toULong())
    fun uint64(value: Number): Cadence.Value = Cadence.uint64(value.toLong().toULong())
    
    fun uint256(value: String): Cadence.Value = Cadence.uint256(BigInteger.parseString(value))
    fun uint256(value: BigDecimal): Cadence.Value = Cadence.uint256(BigInteger.parseString(value.toBigInteger().toString()))
    fun uint256(value: java.math.BigInteger): Cadence.Value = Cadence.uint256(BigInteger.parseString(value.toString()))
    
    fun int(value: Int): Cadence.Value = Cadence.int(value)
    
    fun bool(value: Boolean): Cadence.Value = Cadence.bool(value)
    
    fun array(values: List<Cadence.Value>): Cadence.Value = Cadence.array(values)
    
    fun byteArray(value: ByteArray): Cadence.Value = Cadence.array(value.map { Cadence.uint8(it.toUByte()) })
    
    fun path(domain: String, identifier: String): Cadence.Value {
        val pathDomain = when (domain.lowercase()) {
            "storage" -> Cadence.PathDomain.STORAGE
            "private" -> Cadence.PathDomain.PRIVATE
            "public" -> Cadence.PathDomain.PUBLIC
            else -> throw IllegalArgumentException("Invalid path domain: $domain")
        }
        return Cadence.path(pathDomain, identifier)
    }
    
    fun storagePath(identifier: String): Cadence.Value = path("storage", identifier)
    fun privatePath(identifier: String): Cadence.Value = path("private", identifier)
    fun publicPath(identifier: String): Cadence.Value = path("public", identifier)
    
    fun ufix64Safe(number: Number): Cadence.Value {
        logd("CadenceArguments", "number:$number")
        val value = if (number is Long || number is Int) {
            number.toFloat()
        } else number
        
        // Use DecimalFormat to ensure proper decimal representation without scientific notation
        val formattedValue = try {
            val decimalFormat = java.text.DecimalFormat("0.00000000")
            decimalFormat.format(value).toDouble()
        } catch (e: Exception) {
            // Fallback to the original approach
            "%.8f".format(Locale.US, value).toDouble()
        }
        
        return Cadence.ufix64(formattedValue)
    }
}

fun (CadenceArgumentsBuilder.() -> Unit).builder(): CadenceArgumentsBuilder {
    val argsBuilder = CadenceArgumentsBuilder()
    this(argsBuilder)
    return argsBuilder
}

@WorkerThread
suspend fun FlowAddress.lastBlockAccount(): Account {
    return FlowCadenceApi.getAccount(this.formatted)
}

@WorkerThread
suspend fun FlowAddress.lastBlockAccountKeyId(): Int {
    val account = lastBlockAccount()
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider()
    
    if (cryptoProvider != null) {
        val currentPublicKey = cryptoProvider.getPublicKey().removePrefix("0x").lowercase()
        // Find a key that matches the current crypto provider and is not revoked
        val matchingKey = account.keys?.firstOrNull { key ->
            val keyPublicKey = key.publicKey.removePrefix("0x").lowercase()
            !key.revoked && keyPublicKey == currentPublicKey
        }
        if (matchingKey != null) {
            return matchingKey.index.toInt()
        }
        
        // If we have a crypto provider but no matching non-revoked key, log error and return -1
        logd("FlowUtils", "⚠️ WARNING: No valid non-revoked key found for current crypto provider on account ${this.formatted}")
        logd("FlowUtils", "Available keys: ${account.keys?.map { "index=${it.index}, revoked=${it.revoked}, pubKey=${it.publicKey.take(10)}..." }}")
        logd("FlowUtils", "Current provider public key: ${currentPublicKey.take(10)}...")
        return -1
    }
    
    // If no crypto provider available, fall back to first non-revoked key (legacy behavior)
    val nonRevokedKey = account.keys?.firstOrNull { !it.revoked }
    if (nonRevokedKey != null) {
        return nonRevokedKey.index.toInt()
    }
    
    // If all keys are revoked, return the first key's index (or 0 as fallback)
    return account.keys?.firstOrNull()?.index?.toInt() ?: 0
}

@WorkerThread
suspend fun FlowAddress.payerAccountKeyId(): Int {
    // For payer accounts, we don't have the private keys, so we just return the first non-revoked key
    val account = lastBlockAccount()
    val nonRevokedKey = account.keys?.firstOrNull { !it.revoked }
    if (nonRevokedKey != null) {
        return nonRevokedKey.index.toInt()
    }
    
    // If all keys are revoked, return the first key's index (or 0 as fallback)
    return account.keys?.firstOrNull()?.index?.toInt() ?: 0
}

@WorkerThread
suspend fun FlowAddress.currentKeyId(publicKey: String): Int {
    val account = lastBlockAccount()
    val normalizedPublicKey = publicKey.removePrefix("0x").lowercase()
    // Find a key that matches the public key and is not revoked
    val matchingKey = account.keys?.firstOrNull { key ->
        val keyPublicKey = key.publicKey.removePrefix("0x").lowercase()
        !key.revoked && keyPublicKey == normalizedPublicKey
    }
    if (matchingKey != null) {
        return matchingKey.index.toInt()
    }
    
    // If no matching non-revoked key found, log warning and return -1
    logd("FlowUtils", "⚠️ WARNING: No valid non-revoked key found for public key ${normalizedPublicKey.take(10)}... on account ${this.formatted}")
    logd("FlowUtils", "Available keys: ${account.keys?.map { "index=${it.index}, revoked=${it.revoked}, pubKey=${it.publicKey.take(10)}..." }}")
    return -1
}

