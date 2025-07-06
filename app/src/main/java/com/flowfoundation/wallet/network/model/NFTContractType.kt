package com.flowfoundation.wallet.network.model


enum class NFTContractType {
    ERC721,
    ERC1155,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): NFTContractType {
            return when (value?.uppercase()) {
                "ERC721" -> ERC721
                "ERC1155" -> ERC1155
                else -> UNKNOWN
            }
        }
    }
}