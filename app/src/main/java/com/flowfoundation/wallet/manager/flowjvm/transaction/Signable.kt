package com.flowfoundation.wallet.manager.flowjvm.transaction

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.parcelize.Parcelize
import org.onflow.flow.infrastructure.Cadence
import org.onflow.flow.infrastructure.*

data class Signable(
    @SerializedName("cadence")
    val cadence: String? = null,
    @SerializedName("f_type")
    var fType: String = "Signable",
    @SerializedName("f_vsn")
    val fVsn: String = "1.0.1",
    @SerializedName("keyId")
    val keyId: Int? = null,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("voucher")
    var voucher: Voucher? = null,
)

data class AsArgument(
    @SerializedName("type")
    val type: String,
    @SerializedName("value")
    val value: Any
)

fun AsArgument.toCadenceValue(): Cadence.Value {
    if (value == null) return Cadence.void()

    return when (type) {
        TYPE_ADDRESS -> Cadence.address(value.toString())
        TYPE_STRING -> Cadence.string(value.toString())
        TYPE_BOOLEAN -> Cadence.bool(value.toString().toBoolean())
        TYPE_INT -> Cadence.int(value.toString().toInt())
        TYPE_UINT -> Cadence.uint(value.toString().toUInt())
        TYPE_INT8 -> Cadence.int8(value.toString().toByte())
        TYPE_UINT8 -> Cadence.uint8(value.toString().toUByte())
        TYPE_INT16 -> Cadence.int16(value.toString().toShort())
        TYPE_UINT16 -> Cadence.uint16(value.toString().toUShort())
        TYPE_INT32 -> Cadence.int32(value.toString().toInt())
        TYPE_UINT32 -> Cadence.uint32(value.toString().toUInt())
        TYPE_INT64 -> Cadence.int64(value.toString().toLong())
        TYPE_UINT64 -> Cadence.uint64(value.toString().toULong())
        TYPE_INT128 -> Cadence.int128(BigInteger.parseString(value.toString(), 10))
        TYPE_UINT128 -> Cadence.uint128(BigInteger.parseString(value.toString(), 10))
        TYPE_INT256 -> Cadence.int256(BigInteger.parseString(value.toString(), 10))
        TYPE_UINT256 -> Cadence.uint256(BigInteger.parseString(value.toString(), 10))
        TYPE_FIX64 -> Cadence.fix64(value.toString().toDouble())
        TYPE_UFIX64 -> Cadence.ufix64(value.toString().toDouble())
        "Array" -> {
            if (value is List<*>) {
                val list = value.mapNotNull {
                    (it as? AsArgument)?.toCadenceValue()
                }
                Cadence.array(list)
            } else {
                // Fallback: treat non-list values as strings.
                Cadence.string(value.toString())
            }
        }
        "Dictionary" -> {
            if (value is Map<*, *>) {
                val entries = value.mapNotNull { entry ->
                    val keyArg = entry.key as? AsArgument
                    val valueArg = entry.value as? AsArgument
                    if (keyArg != null && valueArg != null) {
                        Cadence.DictionaryFieldEntry(keyArg.toCadenceValue(), valueArg.toCadenceValue())
                    } else null
                }
                Cadence.dictionary(entries)
            } else {
                Cadence.string(value.toString())
            }
        }
        "Path" -> {
            // Adjust as needed: here we default to Public
            Cadence.path(Cadence.Path(Cadence.PathDomain.PUBLIC, value.toString()))
        }
        "Capability" -> {
            // This is a placeholder—capability conversion might require more details.
            Cadence.capability(value.toString(), value.toString(), Cadence.Type.ADDRESS)
        }
        else -> Cadence.string(value.toString())
    }
}

data class Voucher(
    @SerializedName("arguments")
    val arguments: List<AsArgument>?,
    @SerializedName("authorizers")
    val authorizers: List<String>? = null,
    @SerializedName("cadence")
    val cadence: String?,
    @SerializedName("computeLimit")
    val computeLimit: Int? = null,
    @SerializedName("payer")
    val payer: String?,
    @SerializedName("payloadSigs")
    var payloadSigs: List<Signature>? = null,
    @SerializedName("envelopeSigs")
    val envelopeSigs: List<Signature>? = null,
    @SerializedName("proposalKey")
    val proposalKey: ProposalKey,
    @SerializedName("refBlock")
    val refBlock: String? = null,
)

@Parcelize
data class Signature(
    @SerializedName("address")
    val address: String,
    @SerializedName("keyId")
    val keyId: Int?,
    @SerializedName("sig")
    val sig: String? = null,
) : Parcelable

@Parcelize
data class ProposalKey(
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("keyId")
    val keyId: Int? = null,
    @SerializedName("sequenceNum")
    val sequenceNum: Int? = null,
) : Parcelable
