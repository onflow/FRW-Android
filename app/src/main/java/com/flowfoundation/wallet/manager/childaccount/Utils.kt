package com.flowfoundation.wallet.manager.childaccount

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.res2String

class DataClasses {
    data class Root(
        @SerializedName("value")
        val value: List<ValueItem>
    )

    data class ValueItem(
        @SerializedName("key")
        val key: ValueContainer,
        @SerializedName("value")
        val value: ComplexValue
    )

    data class ValueContainer(
        @SerializedName("value")
        val value: String
    )

    data class ComplexValue(
        @SerializedName("value")
        val value: ComplexValue1?
    )

    data class ComplexValue1(
        @SerializedName("value")
        val value: ComplexValue2?
    )

    data class ComplexValue2(
        @SerializedName("value")
        val value: FieldContainer?
    )

    data class FieldContainer(
        @SerializedName("fields")
        val fields: List<FieldItem>?
    )

    data class FieldItem(
        @SerializedName("name")
        val name: String,
        @SerializedName("value")
        val value: ValueOrNested
    )

    data class ValueOrNested(
        @SerializedName("value")
        val value: JsonElement
    )
}

/**
 * Checks if an address is a COA (Cadence Owned Account)
 * COA addresses start with 0x000000 when in full EVM format
 * @return true if the address is a COA, false otherwise
 */
fun String.isCOAAddress(): Boolean {
    if (!this.startsWith("0x")) {
        return false
    }
    val normalized = this.lowercase()
    return normalized.startsWith("0x000000")
}

/**
 * Convert Flow address to full EVM format for COA addresses
 * COA addresses need to be in 42-character EVM format with leading zeros
 * Example: 0x676e6955fdd27bad -> 0x000000000000000000000000676e6955fdd27bad
 */
private fun String.toFullEVMFormat(): String {
    val addressWithout0x = this.removePrefix("0x")

    // If already 40 characters, it's already in EVM format
    if (addressWithout0x.length == 40) {
        return "0x$addressWithout0x"
    }

    // If it's 16 characters (Flow address), convert to EVM format with leading zeros
    if (addressWithout0x.length == 16) {
        val paddedAddress = addressWithout0x.padStart(40, '0')
        return "0x$paddedAddress"
    }

    // Return as-is for other formats
    return this
}

fun String.parseAccountMetas(): List<ChildAccount> {
    val root = Gson().fromJson(this, DataClasses.Root::class.java)

    return root.value.map { valueItem ->
        val rawAddress = valueItem.key.value
        var name: String? = null
        var icon: String? = null
        var description: String? = null

        valueItem.value.value?.value?.value?.fields?.forEach { fieldItem ->
            when (fieldItem.name) {
                "name" -> {
                    if (fieldItem.value.value.isJsonPrimitive) {
                        name = fieldItem.value.value.asString
                    }
                }

                "thumbnail" -> {
                    if (fieldItem.value.value.isJsonObject) {
                        val thumbnailFields = Gson().fromJson(fieldItem.value.value, DataClasses.FieldContainer::class.java)
                        thumbnailFields.fields?.firstOrNull { it.name == "url" }?.value?.value?.let {
                            if (it.isJsonPrimitive) {
                                icon = it.asString
                            }
                        }
                    }
                }

                "description" -> {
                    if (fieldItem.value.value.isJsonPrimitive) {
                        description = fieldItem.value.value.asString
                    }
                }
            }
        }

        // Convert to full EVM format (COA addresses need 42-char format with leading zeros)
        val address = rawAddress.toFullEVMFormat()

        ChildAccount(
            address = address,
            name = name ?: R.string.default_child_account_name.res2String(),
            icon = icon.orEmpty().ifBlank { "https://lilico.app/placeholder-2.0.png" },
            description = description,
        )
    }
}