package com.flowfoundation.wallet.manager.walletconnect

import androidx.annotation.WorkerThread
import com.nftco.flow.sdk.FlowAddress
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.flowjvm.lastBlockAccountKeyId
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.manager.walletconnect.model.WalletConnectMethod
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.webview.fcl.encodeAccountProof

@WorkerThread
fun walletConnectAuthnServiceResponse(
    address: String,
    keyId: Int,
    nonce: String?,
    appIdentifier: String?,
): String {
    return """
{
  "f_type": "PollingResponse",
  "status": "APPROVED",
  "f_vsn": "1.0.0",
  "data": {
    "f_vsn": "1.0.0",
    "paddr": null,
    "services": [
      ${
        """
            ${authn(address.toAddress(), keyId)},
            ${authz(address.toAddress(), keyId)},
            ${userSign(address.toAddress(), keyId)},
            ${preAuthz()},
            ${signMessage() + if (nonce.isNullOrBlank() || appIdentifier.isNullOrBlank()) "" else ","}
            ${accountProof(address, keyId, nonce, appIdentifier)}
        """.trimIndent()
      }
    ],
    "addr": "${address.toAddress()}",
    "address": "${address.toAddress()}",
    "f_type": "AuthnResponse"
  },
  "type": "FCL:VIEW:RESPONSE"
}
    """.trimIndent()
}

private fun authn(address: String, keyId: Int): String {
    return """
{
    "f_type": "Service",
    "uid": "https://frw-link.lilico.app/wc",
    "provider": {
        "f_type": "ServiceProvider",
        "f_vsn": "1.0.0",
        "name": "Flow Wallet",
        "address": "$address",
        "description": "Flow Wallet is built from the ground up for Flow Blockchain!",
        "color": "#41CC5D",
        "supportEmail": "wallet@flow.com",
        "website": "https://frw-link.lilico.app/wc",
        "icon": "https://lilico.app/frw-logo.png"
    },
    "id": "$address",
    "f_vsn": "1.0.0",
    "endpoint": "flow_authn",
    "type": "authn",
    "identity": { "address": "$address", "keyId": $keyId }
}
    """.trimIndent()
}

private fun authz(address: String, keyId: Int): String {
    return """
{
    "f_type": "Service",
    "method": "WC/RPC",
    "uid": "https://frw-link.lilico.app/wc",
    "f_vsn": "1.0.0",
    "endpoint": "flow_authz",
    "type": "authz",
    "identity": { "address": "$address", "keyId": $keyId }
}
    """.trimIndent()
}

private fun userSign(address: String, keyId: Int): String {
    return """
{
    "f_type": "Service",
    "method": "WC/RPC",
    "uid": "https://frw-link.lilico.app/wc",
    "f_vsn": "1.0.0",
    "endpoint": "flow_user_sign",
    "type": "user-signature",
    "identity": { "address": "$address", "keyId": $keyId }
}
    """.trimIndent()
}

private fun preAuthz(): String {
    return """
{
    "f_type": "Service",
    "f_vsn": "1.0.0",
    "type": "pre-authz",
    "uid": "https://frw-link.lilico.app/wc",
    "endpoint": "flow_pre_authz",
    "method": "WC/RPC",
    "data": {
      "address": "${AppConfig.payer().address.toAddress()}",
      "keyId": ${FlowAddress(AppConfig.payer().address.toAddress()).lastBlockAccountKeyId()}
    }
}
    """.trimIndent()
}

private fun accountProof(address: String, keyId: Int, nonce: String?, appIdentifier: String?): String {
    if (nonce.isNullOrBlank() || appIdentifier.isNullOrBlank()) return ""
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return ""
    val accountProofSign = cryptoProvider.signData(encodeAccountProof(address, nonce, appIdentifier,
        includeDomainTag = true))
    return """
    {
        "f_type": "Service",
        "f_vsn": "1.0.0",
        "type": "account-proof",
        "uid": "https://frw-link.lilico.app/wc",
        "endpoint": "${WalletConnectMethod.ACCOUNT_PROOF.value}",
        "method": "WC/RPC",
        "data": {
          "f_type": "account-proof",
          "f_vsn": "2.0.0",
          "address": "$address",
          "nonce": "$nonce",
          "signatures": [
            {
              "f_type": "CompositeSignature",
              "f_vsn": "1.0.0",
              "addr": "$address",
              "keyId": $keyId,
              "signature": "$accountProofSign"
            }
          ]
        }
    }
""".trimIndent()
}

private fun signMessage(): String {
    return """
    {
        "f_type": "Service",
        "f_vsn": "1.0.0",
        "type": "user-signature",
        "uid": "https://frw-link.lilico.app/wc",
        "endpoint": "${WalletConnectMethod.USER_SIGNATURE.value}",
        "method": "WC/RPC"
    }
""".trimIndent()
}
