package com.flowfoundation.wallet.manager.flowjvm

import com.flowfoundation.wallet.manager.cadence.CadenceApiManager


enum class CadenceScript(val scriptId: String, val type: CadenceScriptType) {
    // BASIC
    CADENCE_CHECK_TOKEN_IS_ENABLED("isTokenStorageEnabled", CadenceScriptType.BASIC),
    CADENCE_GET_BALANCE("getTokenBalanceWithModel", CadenceScriptType.BASIC),
    CADENCE_ADD_PUBLIC_KEY("addKey", CadenceScriptType.BASIC),
    CADENCE_QUERY_ADDRESS_BY_DOMAIN_FLOWNS("getFlownsAddress", CadenceScriptType.BASIC),
    CADENCE_QUERY_ADDRESS_BY_DOMAIN_FIND("getFindAddress", CadenceScriptType.BASIC),
    CADENCE_QUERY_STORAGE_INFO("getStorageInfo", CadenceScriptType.BASIC),
    CADENCE_QUERY_MIN_FLOW_BALANCE("getAccountMinFlow", CadenceScriptType.BASIC),
    CADENCE_REVOKE_ACCOUNT_KEY("revokeKey", CadenceScriptType.BASIC),
    CADENCE_GET_ACCOUNT_INFO("getAccountInfo", CadenceScriptType.BASIC),

    // FT
    CADENCE_TRANSFER_TOKEN("transferTokensV3", CadenceScriptType.FT),
    CADENCE_ADD_TOKEN("addToken", CadenceScriptType.FT),
    CADENCE_GET_TOKEN_LIST_BALANCE("getTokenListBalance", CadenceScriptType.FT),
    CADENCE_CHECK_TOKEN_LIST_ENABLED("isTokenListEnabled", CadenceScriptType.FT),
    CADENCE_CHECK_LINKED_ACCOUNT_TOKEN_LIST_ENABLED("isLinkedAccountTokenListEnabled", CadenceScriptType.FT),

    // NFT
    CADENCE_CHECK_NFT_LIST_ENABLED("checkNFTListEnabled", CadenceScriptType.NFT),

    // COLLECTION
    CADENCE_NFT_ENABLE("enableNFTStorage", CadenceScriptType.COLLECTION),
    CADENCE_NFT_TRANSFER("sendNFTV3", CadenceScriptType.COLLECTION),
    CADENCE_NBA_NFT_TRANSFER("sendNbaNFTV3", CadenceScriptType.COLLECTION),

    // SWAP
    CADENCE_SWAP_EXACT_TOKENS_TO_OTHER_TOKENS("SwapExactTokensForTokens", CadenceScriptType.SWAP),
    CADENCE_SWAP_TOKENS_FROM_EXACT_TOKENS("SwapTokensForExactTokens", CadenceScriptType.SWAP),

    // STAKING
    CADENCE_CREATE_STAKE_DELEGATOR_ID("createDelegator", CadenceScriptType.STAKING),
    CADENCE_STAKE_FLOW("createStake", CadenceScriptType.STAKING),
    CADENCE_UNSTAKE_FLOW("unstake", CadenceScriptType.STAKING),
    CADENCE_QUERY_STAKE_INFO("getDelegatesInfoArrayV2", CadenceScriptType.STAKING),
    CADENCE_GET_STAKE_APY_BY_WEEK("getApyWeekly", CadenceScriptType.STAKING),
    CADENCE_GET_STAKE_APY_BY_YEAR("getApr", CadenceScriptType.STAKING),
    CADENCE_CHECK_IS_STAKING_SETUP("checkSetup", CadenceScriptType.STAKING),
    CADENCE_SETUP_STAKING("setup", CadenceScriptType.STAKING),
    CADENCE_CHECK_STAKING_ENABLED("checkStakingEnabled", CadenceScriptType.STAKING),
    CADENCE_GET_DELEGATOR_INFO("getDelegatesIndo", CadenceScriptType.STAKING),
    CADENCE_CLAIM_REWARDS("withdrawReward", CadenceScriptType.STAKING),
    CADENCE_RESTAKE_REWARDS("restakeReward", CadenceScriptType.STAKING),
    CADENCE_STAKING_UNSATKED_CLAIM("withdrawUnstaked", CadenceScriptType.STAKING),
    CADENCE_STAKING_UNSATKED_RESTAKE("restakeUnstaked", CadenceScriptType.STAKING),

    // HYBRID_CUSTODY
    CADENCE_QUERY_CHILD_ACCOUNT_META("getChildAccountMeta", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_UNLINK_CHILD_ACCOUNT("unlinkChildAccount", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_EDIT_CHILD_ACCOUNT("editChildAccount", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_QUERY_CHILD_ACCOUNT_TOKENS("getAccessibleCoinInfo", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_QUERY_CHILD_ACCOUNT_NFT_COLLECTIONS("getChildAccountAllowTypes", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_BRIDGE_CHILD_NFT_TO_EVM("bridgeChildNFTToEvm", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_BRIDGE_CHILD_NFT_FROM_EVM("bridgeChildNFTFromEvm", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_BRIDGE_CHILD_FT_TO_EVM("bridgeChildFTToEvm", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_BRIDGE_CHILD_FT_FROM_EVM("bridgeChildFTFromEvm", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_BRIDGE_CHILD_NFT_LIST_TO_EVM("batchBridgeChildNFTToEvm", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_BRIDGE_CHILD_NFT_LIST_FROM_EVM("batchBridgeChildNFTFromEvm", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_MOVE_NFT_FROM_CHILD_TO_PARENT("transferChildNFT", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_SEND_NFT_FROM_CHILD_TO_FLOW("sendChildNFT", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_SEND_NFT_FROM_CHILD_TO_CHILD("sendChildNFTToChild", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_SEND_NFT_LIST_FROM_CHILD_TO_CHILD("batchSendChildNFTToChild", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_SEND_NFT_FROM_PARENT_TO_CHILD("transferNFTToChild", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_SEND_NFT_LIST_FROM_PARENT_TO_CHILD("batchTransferNFTToChild", CadenceScriptType.HYBRID_CUSTODY),
    CADENCE_MOVE_NFT_LIST_FROM_CHILD_TO_PARENT("batchTransferChildNFT", CadenceScriptType.HYBRID_CUSTODY),

    // BRIDGE
    CADENCE_GET_ASSOCIATED_FLOW_IDENTIFIER("getAssociatedFlowIdentifier", CadenceScriptType.BRIDGE),
    CADENCE_BRIDGE_FT_TO_EVM("bridgeTokensToEvmV2", CadenceScriptType.BRIDGE),
    CADENCE_BRIDGE_FT_FROM_FLOW_TO_EVM("bridgeTokensToEvmAddressV2", CadenceScriptType.BRIDGE),
    CADENCE_BRIDGE_FT_FROM_EVM("bridgeTokensFromEvmV2", CadenceScriptType.BRIDGE),
    CADENCE_BRIDGE_FT_FROM_EVM_TO_FLOW("bridgeTokensFromEvmToFlowV3", CadenceScriptType.BRIDGE),
    CADENCE_BRIDGE_NFT_TO_EVM("bridgeNFTToEvmV2", CadenceScriptType.BRIDGE),
    CADENCE_BRIDGE_NFT_FROM_EVM("bridgeNFTFromEvmV2", CadenceScriptType.BRIDGE),
    CADENCE_BRIDGE_NFT_LIST_TO_EVM("batchBridgeNFTToEvmV2", CadenceScriptType.BRIDGE),
    CADENCE_BRIDGE_NFT_LIST_FROM_EVM("batchBridgeNFTFromEvmV2", CadenceScriptType.BRIDGE),
    CADENCE_BRIDGE_NFT_FROM_FLOW_TO_EVM("bridgeNFTToEvmAddressV2", CadenceScriptType.BRIDGE),
    CADENCE_BRIDGE_NFT_FROM_EVM_TO_FLOW("bridgeNFTFromEvmToFlowV3", CadenceScriptType.BRIDGE),

    // EVM
    CADENCE_CREATE_COA_ACCOUNT("createCoaEmpty", CadenceScriptType.EVM),
    CADENCE_CHECK_COA_LINK("checkCoaLink", CadenceScriptType.EVM),
    CADENCE_COA_LINK("coaLink", CadenceScriptType.EVM),
    CADENCE_QUERY_COA_EVM_ADDRESS("getCoaAddr", CadenceScriptType.EVM),
    CADENCE_QUERY_COA_FLOW_BALANCE("getCoaBalance", CadenceScriptType.EVM),
    CADENCE_FUND_COA_FLOW_BALANCE("fundCoa", CadenceScriptType.EVM),
    CADENCE_WITHDRAW_COA_FLOW_BALANCE("withdrawCoa", CadenceScriptType.EVM),
    CADENCE_TRANSFER_FLOW_TO_EVM("transferFlowToEvmAddress", CadenceScriptType.EVM),
    CADENCE_CALL_EVM_CONTRACT("callContract", CadenceScriptType.EVM),
    CADENCE_CALL_EVM_CONTRACT_V2("callContractV2", CadenceScriptType.EVM),
    CADENCE_GET_NONCE("getNonce", CadenceScriptType.EVM),

    // DOMAIN
    CADENCE_CLAIM_INBOX_TOKEN("claimFTFromInbox", CadenceScriptType.DOMAIN),
    CADENCE_CLAIM_INBOX_NFT("claimNFTFromInbox", CadenceScriptType.DOMAIN);

    fun getScript(): String {
        return when (type) {
            CadenceScriptType.BASIC -> CadenceApiManager.getCadenceBasicScript(scriptId)
            CadenceScriptType.ACCOUNT -> CadenceApiManager.getCadenceAccountScript(scriptId)
            CadenceScriptType.COLLECTION -> CadenceApiManager.getCadenceCollectionScript(scriptId)
            CadenceScriptType.CONTRACT -> CadenceApiManager.getCadenceContractScript(scriptId)
            CadenceScriptType.DOMAIN -> CadenceApiManager.getCadenceDomainScript(scriptId)
            CadenceScriptType.FT -> CadenceApiManager.getCadenceFTScript(scriptId)
            CadenceScriptType.HYBRID_CUSTODY -> CadenceApiManager.getCadenceHybridCustodyScript(scriptId)
            CadenceScriptType.STAKING -> CadenceApiManager.getCadenceStakingScript(scriptId)
            CadenceScriptType.STORAGE -> CadenceApiManager.getCadenceStorageScript(scriptId)
            CadenceScriptType.EVM -> CadenceApiManager.getCadenceEVMScript(scriptId)
            CadenceScriptType.NFT -> CadenceApiManager.getCadenceNFTScript(scriptId)
            CadenceScriptType.SWAP -> CadenceApiManager.getCadenceSwapScript(scriptId)
            CadenceScriptType.BRIDGE -> CadenceApiManager.getCadenceBridgeScript(scriptId)
        }
    }
}

enum class CadenceScriptType {
    BASIC,
    ACCOUNT,
    COLLECTION,
    CONTRACT,
    DOMAIN,
    FT,
    HYBRID_CUSTODY,
    STAKING,
    STORAGE,
    EVM,
    NFT,
    SWAP,
    BRIDGE;
}
