package com.flowfoundation.wallet.manager.flowjvm.transaction

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

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
