package com.flowfoundation.wallet.manager.cadence

data class CadenceScriptResponse(
    val data: CadenceScriptData?,
    val status: Int
)


data class CadenceScriptData(
    val scripts: CadenceScripts,
    val version: String
)

data class CadenceScripts(
    val mainnet: CadenceScript,
    val testnet: CadenceScript,
    val previewnet: CadenceScript
)

data class CadenceScript(
    val version: String,
    val basic: Map<String, String>?,
    val account: Map<String, String>?,
    val collection: Map<String, String>?,
    val contract: Map<String, String>?,
    val domain: Map<String, String>?,
    val ft: Map<String, String>?,
    val hybridCustody: Map<String, String>?,
    val staking: Map<String, String>?,
    val storage: Map<String, String>?,
    val switchboard: Map<String, String>?,
    val evm: Map<String, String>?,
    val nft: Map<String, String>?,
    val swap: Map<String, Any>?,
    val bridge: Map<String, String>?
)