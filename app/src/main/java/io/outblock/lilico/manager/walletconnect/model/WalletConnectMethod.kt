package io.outblock.lilico.manager.walletconnect.model

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
}