package com.flowfoundation.wallet.manager.evm

enum class DAppMethod {
    SIGN_TRANSACTION,
    SIGN_PERSONAL_MESSAGE,
    SIGN_MESSAGE,
    SIGN_TYPED_MESSAGE,
    EC_RECOVER,
    REQUEST_ACCOUNTS,
    WATCH_ASSET,
    ADD_ETHEREUM_CHAIN,
    SWITCH_ETHEREUM_CHAIN,
    UNKNOWN;

    companion object {
        fun fromValue(value: String): DAppMethod {
            return when (value) {
                "signTransaction" -> SIGN_TRANSACTION
                "signPersonalMessage" -> SIGN_PERSONAL_MESSAGE
                "signMessage" -> SIGN_MESSAGE
                "signTypedMessage" -> SIGN_TYPED_MESSAGE
                "ecRecover" -> EC_RECOVER
                "requestAccounts" -> REQUEST_ACCOUNTS
                "watchAsset" -> WATCH_ASSET
                "addEthereumChain" -> ADD_ETHEREUM_CHAIN
                "switchEthereumChain" -> SWITCH_ETHEREUM_CHAIN
                else -> UNKNOWN
            }
        }
    }
}