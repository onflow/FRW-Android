package com.flowfoundation.wallet.widgets.webview.fcl

import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.nftco.flow.sdk.DomainTag
import com.nftco.flow.sdk.FlowAddress
import org.onflow.flow.models.hexToBytes
import com.flowfoundation.wallet.manager.config.AppConfig
import com.flowfoundation.wallet.manager.config.isGasFree
import com.flowfoundation.wallet.manager.flowjvm.currentKeyId
import com.flowfoundation.wallet.manager.key.CryptoProviderManager
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclAuthnResponse

private const val PRE_AUTHZ_REPLACEMENT = "#pre-authz"
private const val ADDRESS_REPLACEMENT = "#address"
private const val KEY_ID_REPLACEMENT = "#key-id"
private const val PAYER_ADDRESS_REPLACEMENT = "#payer-address"
private const val SIGNATURE_REPLACEMENT = "#signature"
private const val USER_SIGNATURE_REPLACEMENT = "#user-signature"
private const val ACCOUNT_PROOF_REPLACEMENT = "#account-proof"
private const val NONCE_REPLACEMENT = "#nonce"
private const val NETWORK_REPLACEMENT = "#network"


private val FCL_AUTHN_RESPONSE = """
    {
      "f_type": "PollingResponse",
      "f_vsn": "1.0.0",
      "status": "APPROVED",
      "reason": null,
      "data": {
        "f_type": "AuthnResponse",
        "f_vsn": "1.0.0",
        "addr": "$ADDRESS_REPLACEMENT",
        "services": [
          {
            "f_type": "Service",
            "f_vsn": "1.0.0",
            "type": "authn",
            "uid": "frw#authn",
            "endpoint": "ext:0x000",
            "id": "$ADDRESS_REPLACEMENT",
            "identity": {
              "address": "$ADDRESS_REPLACEMENT"
            },
            "provider": {
              "f_type": "ServiceProvider",
              "f_vsn": "1.0.0",
              "address": "$ADDRESS_REPLACEMENT",
              "name": "Flow Wallet",
              "description": "Flow Wallet is built from the ground up for Flow Blockchain!",
              "color": "#41CC5D",
              "supportEmail": "wallet@flow.com",
              "website": "https://frw-link.lilico.app/wc",
              "icon": "https://lilico.app/logo_mobile.png"
            }
          },
          $PRE_AUTHZ_REPLACEMENT
          $USER_SIGNATURE_REPLACEMENT
          $ACCOUNT_PROOF_REPLACEMENT
          {
            "f_type": "Service",
            "f_vsn": "1.0.0",
            "type": "authz",
            "uid": "frw#authz",
            "endpoint": "ext:0x000",
            "method": "EXT/RPC",
            "identity": {
              "address": "$ADDRESS_REPLACEMENT",
              "keyId": $KEY_ID_REPLACEMENT
            }
          }
        ],
        "paddr": null
      },
      "type": "FCL:VIEW:RESPONSE"
    }
""".trimIndent()

private val FCL_AUTHN_RESPONSE_USER_SIGNATURE = """
    {
        "f_type": "Service",
        "f_vsn": "1.0.0",
        "type": "user-signature",
        "uid": "frw#user-signature",
        "endpoint": "chrome-extension://hpclkefagolihohboafpheddmmgdffjm/popup.html",
        "method": "EXT/RPC"
    },
""".trimIndent()

private val FCL_AUTHN_RESPONSE_ACCOUNT_PROOF = """
    {
        "f_type": "Service",
        "f_vsn": "1.0.0",
        "type": "account-proof",
        "uid": "frw#account-proof",
        "endpoint": "chrome-extension://hpclkefagolihohboafpheddmmgdffjm/popup.html",
        "method": "EXT/RPC",
        "network": "$NETWORK_REPLACEMENT",
        "data": {
          "f_type": "account-proof",
          "f_vsn": "2.0.0",
          "address": "$ADDRESS_REPLACEMENT",
          "nonce": "$NONCE_REPLACEMENT",
          "signatures": [
            {
              "f_type": "CompositeSignature",
              "f_vsn": "1.0.0",
              "addr": "$ADDRESS_REPLACEMENT",
              "keyId": $KEY_ID_REPLACEMENT,
              "signature": "$SIGNATURE_REPLACEMENT"
            }
          ]
        }
    },
""".trimIndent()

private val FCL_AUTHZ_RESPONSE = """
    {
      "f_type": "PollingResponse",
      "f_vsn": "1.0.0",
      "status": "APPROVED",
      "reason": null,
      "data": {
        "f_type": "CompositeSignature",
        "f_vsn": "1.0.0",
        "addr": "$ADDRESS_REPLACEMENT",
        "keyId": $KEY_ID_REPLACEMENT,
        "signature": "$SIGNATURE_REPLACEMENT"
      },
      "type": "FCL:VIEW:RESPONSE"
    }
""".trimIndent()

private val FCL_PRE_AUTHZ_RESPONSE = """
    {
        "status": "APPROVED",
        "data": {
            "f_type": "PreAuthzResponse",
            "f_vsn": "1.0.0",
            "proposer": {
                "f_type": "Service",
                "f_vsn": "1.0.0",
                "type": "authz",
                "uid": "frw#authz",
                "endpoint": "chrome-extension://hpclkefagolihohboafpheddmmgdffjm/popup.html",
                "method": "EXT/RPC",
                "identity": {
                    "address": "$ADDRESS_REPLACEMENT",
                    "keyId": $KEY_ID_REPLACEMENT
                }
            },
            "payer": [
                {
                    "f_type": "Service",
                    "f_vsn": "1.0.0",
                    "type": "authz",
                    "uid": "frw#authz",
                    "endpoint": "chrome-extension://hpclkefagolihohboafpheddmmgdffjm/popup.html",
                    "method": "EXT/RPC",
                    "identity": {
                        "address": "$PAYER_ADDRESS_REPLACEMENT",
                        "keyId": 0
                    }
                }
            ],
            "authorization": [
                {
                    "f_type": "Service",
                    "f_vsn": "1.0.0",
                    "type": "authz",
                    "uid": "frw#authz",
                    "endpoint": "chrome-extension://hpclkefagolihohboafpheddmmgdffjm/popup.html",
                    "method": "EXT/RPC",
                    "identity": {
                        "address": "$ADDRESS_REPLACEMENT",
                        "keyId": $KEY_ID_REPLACEMENT
                    }
                }
            ]
        },
        "type": "FCL:VIEW:RESPONSE"
    }
""".trimIndent()

private val FCL_SIGN_MESSAGE_RESPONSE = """
    {
      "f_type": "PollingResponse",
      "f_vsn": "1.0.0",
      "status": "APPROVED",
      "reason": null,
      "data": {
        "f_type": "CompositeSignature",
        "f_vsn": "1.0.0",
        "addr": "$ADDRESS_REPLACEMENT",
        "keyId": $KEY_ID_REPLACEMENT,
        "signature": "$SIGNATURE_REPLACEMENT"
      },
      "type": "FCL:VIEW:RESPONSE"
    }
""".trimIndent()

/**
 * dApp login button
 */
fun generateFclExtensionInject(): String {
    val address = "0x33f75ff0b830dcec"
    // Keep mainnet address for now
    // if (isTestnet()) "0x3d2b4d1b51f3a4cd" else "0x33f75ff0b830dcec"
    return """
        {
          f_type: 'Service',
          f_vsn: '1.0.0',
          type: 'authn',
          uid: 'Flow Wallet',
          endpoint: 'chrome-extension://hpclkefagolihohboafpheddmmgdffjm/popup.html',
          method: 'EXT/RPC',
          id: 'hpclkefagolihohboafpheddmmgdffjm',
          identity: {
            address: '$address',
          },
          provider: {
            address: '$address',
            name: 'Flow Wallet',
            icon: 'https://lilico.app/fcw-logo.png',
            description: 'Digital wallet created for everyone.',
          }
        }
""".trimIndent()
}

suspend fun fclAuthnResponse(fcl: FclAuthnResponse, address: String): String {
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: return ""

    val accountProofSign = if (!fcl.body.nonce.isNullOrBlank()) {
        try {
            cryptoProvider.signData(fcl.encodeAccountProof(address))
        } catch (e: Exception) {
            ""
        }
    } else ""

    val keyId = FlowAddress(address).currentKeyId(cryptoProvider.getPublicKey())
    return fclAuthnResponseWithAccountProofSign(accountProofSign, fcl.body.nonce, address, keyId)
}

suspend fun fclAuthnResponseWithAccountProofSign(
    accountProofSign: String? = null,
    nonce: String? = null,
    address: String,
    keyId: Int
): String {
    return FCL_AUTHN_RESPONSE
        .replace(ADDRESS_REPLACEMENT, address)
        .replace(KEY_ID_REPLACEMENT, "$keyId")
        .replace(PRE_AUTHZ_REPLACEMENT, generateAuthnPreAuthz())
        .replace(USER_SIGNATURE_REPLACEMENT, FCL_AUTHN_RESPONSE_USER_SIGNATURE)
        .replace(
            ACCOUNT_PROOF_REPLACEMENT,
            if (accountProofSign.isNullOrEmpty()) "" else FCL_AUTHN_RESPONSE_ACCOUNT_PROOF.replace(ADDRESS_REPLACEMENT, address)
                .replace(SIGNATURE_REPLACEMENT, accountProofSign).replace(NONCE_REPLACEMENT,
                    nonce.orEmpty()).replace(KEY_ID_REPLACEMENT, "$keyId").replace(NETWORK_REPLACEMENT, chainNetWorkString())
        )
}

fun fclPreAuthzResponse(address: String, keyId: Int): String {
    return FCL_PRE_AUTHZ_RESPONSE
        .replace(ADDRESS_REPLACEMENT, address)
        .replace(KEY_ID_REPLACEMENT, "$keyId")
        .replace(PAYER_ADDRESS_REPLACEMENT, AppConfig.payer().address)
}

fun fclAuthzResponse(address: String, signature: String, keyId: Int? = 0): String {
    return FCL_AUTHZ_RESPONSE
        .replace(ADDRESS_REPLACEMENT, address)
        .replace(SIGNATURE_REPLACEMENT, signature)
        .replace(KEY_ID_REPLACEMENT, "$keyId")
}

suspend fun fclSignMessageResponse(message: String?, address: String): String {
    val messageBytes = message?.hexToBytes() ?: throw IllegalArgumentException("Message is empty")
    val cryptoProvider = CryptoProviderManager.getCurrentCryptoProvider() ?: throw IllegalArgumentException("Crypto Provider is null")

    val keyId = FlowAddress(address).currentKeyId(cryptoProvider.getPublicKey())
    return FCL_SIGN_MESSAGE_RESPONSE
        .replace(ADDRESS_REPLACEMENT, address)
        .replace(KEY_ID_REPLACEMENT, "$keyId")
        .replace(SIGNATURE_REPLACEMENT, cryptoProvider.signData(DomainTag.USER_DOMAIN_TAG + messageBytes))
}

private suspend fun generateAuthnPreAuthz(): String {
    return if (isGasFree()) {
        """
            {
                "f_type": "Service",
                "f_vsn": "1.0.0",
                "type": "pre-authz",
                "uid": "frw#pre-authz",
                "endpoint": "android://pre-authz.lilico.app",
                "method": "EXT/RPC",
                "data": {
                    "address": "${AppConfig.payer().address.toAddress()}",
                    "keyId": 0
                }
            },
        """.trimIndent()
    } else ""
}

