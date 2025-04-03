package com.flowfoundation.wallet.manager.walletconnect.model

enum class WalletConnectMethod(val value: String) {
    AUTHN("flow_authn"),
    AUTHZ("flow_authz"),
    PRE_AUTHZ("flow_pre_authz"),
    SIGN_PAYER("flow_sign_payer"),
    SIGN_PROPOSER("flow_sign_proposer"),
    USER_SIGNATURE("flow_user_sign"),
    ACCOUNT_PROOF("flow_account_proof"),
    ACCOUNT_INFO("frw_account_info"),
    ADD_DEVICE_KEY("frw_add_device_key"),
    PROXY_ACCOUNT("frw_proxy_account"),
    PROXY_SIGN("frw_proxy_sign"),
    EVM_SIGN_MESSAGE("personal_sign"),
    EVM_SEND_TRANSACTION("eth_sendTransaction"),
    EVM_SIGN_TYPED_DATA("eth_signTypedData"),
    EVM_SIGN_TYPED_DATA_V3("eth_signTypedData_v3"),
    EVM_SIGN_TYPED_DATA_V4("eth_signTypedData_v4"),
    WALLET_WATCH_ASSETS("wallet_watchAsset");

    companion object {

        @JvmStatic
        fun getSupportedFlowMethod(): List<String> {
            return listOf(AUTHN.value, AUTHZ.value, PRE_AUTHZ.value, SIGN_PAYER.value,
                SIGN_PROPOSER.value, USER_SIGNATURE.value, ACCOUNT_PROOF.value, ACCOUNT_INFO
                    .value, ADD_DEVICE_KEY.value, PROXY_ACCOUNT.value, PROXY_SIGN.value)
        }

        @JvmStatic
        fun getSupportedEVMMethod(): List<String> {
            return listOf(EVM_SIGN_MESSAGE.value, EVM_SEND_TRANSACTION.value, EVM_SIGN_TYPED_DATA.value,
                EVM_SIGN_TYPED_DATA_V3.value,
                EVM_SIGN_TYPED_DATA_V4.value, WALLET_WATCH_ASSETS.value)
        }

    }
}