package io.outblock.lilico.manager.walletconnect

import androidx.annotation.WorkerThread
import com.nftco.flow.sdk.FlowAddress
import io.outblock.lilico.manager.config.AppConfig
import io.outblock.lilico.manager.flowjvm.lastBlockAccountKeyId
import io.outblock.lilico.manager.key.CryptoProviderManager
import io.outblock.lilico.manager.walletconnect.model.WalletConnectMethod
import io.outblock.lilico.wallet.removeAddressPrefix
import io.outblock.lilico.wallet.toAddress
import io.outblock.lilico.widgets.webview.fcl.encodeAccountProof

@WorkerThread
fun walletConnectAuthnServiceResponse(
    address: String,
    keyId: Int,
    nonce: String?,
    appIdentifier: String?,
    isFromSdk: Boolean,
): String {
    return """
{
  "f_type": "PollingResponse",
  "status": "APPROVED",
  "f_vsn": "1.0.0",
  "data": {
    "fVsn": "1.0.0",
    "paddr": null,
    "services": [
      ${
        if (isFromSdk) {
            """
                  ${authn(address.removeAddressPrefix(), keyId)},
                  ${authz(address.removeAddressPrefix(), keyId)},
                  ${userSign(address.removeAddressPrefix(), keyId)},
                  ${preAuthz()},
                  ${signMessage()},
                  ${accountProof(address, keyId, nonce, appIdentifier)}${if (nonce.isNullOrBlank() || appIdentifier.isNullOrBlank()) "" else ","}
              """.trimIndent()
        } else {
            """
                  ${authn(address.removeAddressPrefix(), keyId)},
                  ${authz(address.removeAddressPrefix(), keyId)},
                  ${userSign(address.removeAddressPrefix(), keyId)}
              """.trimIndent()
        }
    }
    ],
    "addr": "${address.toAddress()}",
    "address": "${address.toAddress()}",
    "fType": "AuthnResponse"
  }
}
    """.trimIndent()
}

private fun authn(address: String, keyId: Int): String {
    return """
{
    "f_type": "Service",
    "uid": "flow-wallet#authn",
    "provider": {
        "f_type": "ServiceProvider",
        "f_vsn": "1.0.0",
        "name": "Flow Wallet",
        "address": "$address"
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
    "uid": "flow-wallet#authz",
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
    "uid": "flow-wallet#user-signature",
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
    "uid": "lilico#pre-authz",
    "endpoint": "flow_pre_authz",
    "method": "WC/RPC",
    "data": {
      "address": "${AppConfig.payer().address.removeAddressPrefix()}",
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
        "uid": "lilico#account-proof",
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
        "uid": "lilico#user-signature",
        "endpoint": "${WalletConnectMethod.USER_SIGNATURE.value}",
        "method": "WC/RPC"
    }
""".trimIndent()
}
