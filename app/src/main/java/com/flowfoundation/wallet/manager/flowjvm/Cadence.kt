@file:Suppress("UNUSED_PARAMETER")

package com.flowfoundation.wallet.manager.flowjvm

import com.flowfoundation.wallet.manager.cadence.CadenceApiManager

// check coin token is contains in wallet
var CADENCE_CHECK_TOKEN_IS_ENABLED
    get() = CadenceApiManager.getCadenceBasicScript("isTokenStorageEnabled")
    private set(value) {}

var CADENCE_GET_BALANCE
    get() = CadenceApiManager.getCadenceBasicScript("getTokenBalanceWithModel")
    private set(value) {}

var CADENCE_TRANSFER_TOKEN
    get() = CadenceApiManager.getCadenceFTScript("transferTokens")
    private set(value) {}

// enable new coin token for wallet
var CADENCE_ADD_TOKEN
    get() = CadenceApiManager.getCadenceFTScript("addToken")
    private set(value) {}

var CADENCE_GET_TOKEN_LIST_BALANCE
    get() = CadenceApiManager.getCadenceFTScript("getTokenListBalance")
    private set(value) {}

var CADENCE_CHECK_TOKEN_LIST_ENABLED
    get() = CadenceApiManager.getCadenceFTScript("isTokenListEnabled")
    private set(value) {}

var CADENCE_CHECK_LINKED_ACCOUNT_TOKEN_LIST_ENABLED
    get() = CadenceApiManager.getCadenceFTScript("isLinkedAccountTokenListEnabled")
    private set(value) {}

var CADENCE_ADD_PUBLIC_KEY: String
    get() = CadenceApiManager.getCadenceBasicScript("addKey")
    private set(value) {}

var CADENCE_QUERY_ADDRESS_BY_DOMAIN_FLOWNS
    get() = CadenceApiManager.getCadenceBasicScript("getFlownsAddress")
    private set(value) {}

var CADENCE_QUERY_DOMAIN_BY_ADDRESS_FLOWNS
    get() = CadenceApiManager.getCadenceBasicScript("getFlownsDomainsByAddress")
    private set(value) {}

var CADENCE_QUERY_ADDRESS_BY_DOMAIN_FIND
    get() = CadenceApiManager.getCadenceBasicScript("getFindAddress")
    private set(value) {}

var CADENCE_QUERY_DOMAIN_BY_ADDRESS_FIND
    get() = CadenceApiManager.getCadenceBasicScript("getFindDomainByAddress")
    private set(value) {}

var CADENCE_CHECK_NFT_LIST_ENABLED
    get() = CadenceApiManager.getCadenceNFTScript("checkNFTListEnabled")
    private set(value) {}

var CADENCE_NFT_CHECK_ENABLED
    get() = CadenceApiManager.getCadenceCollectionScript("checkNFTCollection")
    private set(value) {}

var CADENCE_NFT_ENABLE
    get() = CadenceApiManager.getCadenceCollectionScript("enableNFTStorage")
    private set(value) {}

var CADENCE_NFT_TRANSFER
    get() = CadenceApiManager.getCadenceCollectionScript("sendNFT")
    private set(value) {}

var CADENCE_NBA_NFT_TRANSFER
    get() = CadenceApiManager.getCadenceCollectionScript("sendNbaNFT")
    private set(value) {}

var CADENCE_CLAIM_INBOX_TOKEN
    get() = CadenceApiManager.getCadenceDomainScript("claimFTFromInbox")
    private set(value) {}

var CADENCE_CLAIM_INBOX_NFT
    get() = CadenceApiManager.getCadenceDomainScript("claimNFTFromInbox")
    private set(value) {}

// want use how many token to swap other token
var CADENCE_SWAP_EXACT_TOKENS_TO_OTHER_TOKENS
    get() = CadenceApiManager.getCadenceSwapScript("SwapExactTokensForTokens")
    private set(value) {}

// want swap how many other token
var CADENCE_SWAP_TOKENS_FROM_EXACT_TOKENS
    get() = CadenceApiManager.getCadenceSwapScript("SwapTokensForExactTokens")
    private set(value) {}

var CADENCE_CREATE_STAKE_DELEGATOR_ID
    get() = CadenceApiManager.getCadenceStakingScript("createDelegator")
    private set(value) {}

var CADENCE_STAKE_FLOW
    get() = CadenceApiManager.getCadenceStakingScript("createStake")
    private set(value) {}

var CADENCE_UNSTAKE_FLOW
    get() = CadenceApiManager.getCadenceStakingScript("unstake")
    private set(value) {}

var CADENCE_QUERY_STAKE_INFO
    get() = CadenceApiManager.getCadenceStakingScript("getDelegatesInfoArray")
    private set(value) {}

var CADENCE_GET_STAKE_APY_BY_WEEK
    get() = CadenceApiManager.getCadenceStakingScript("getApyWeekly")
    private set(value) {}

var CADENCE_GET_STAKE_APY_BY_YEAR
    get() = CadenceApiManager.getCadenceStakingScript("getApr")
    private set(value) {}

var CADENCE_CHECK_IS_STAKING_SETUP
    get() = CadenceApiManager.getCadenceStakingScript("checkSetup")
    private set(value) {}

var CADENCE_SETUP_STAKING
    get() = CadenceApiManager.getCadenceStakingScript("setup")
    private set(value) {}

var CADENCE_CHECK_STAKING_ENABLED
    get() = CadenceApiManager.getCadenceStakingScript("checkStakingEnabled")
    private set(value) {}

var CADENCE_GET_DELEGATOR_INFO
    get() = CadenceApiManager.getCadenceStakingScript("getDelegatesIndo")
    private set(value) {}

var CADENCE_CLAIM_REWARDS
    get() = CadenceApiManager.getCadenceStakingScript("withdrawReward")
    private set(value) {}

var CADENCE_RESTAKE_REWARDS
    get() = CadenceApiManager.getCadenceStakingScript("restakeReward")
    private set(value) {}

var CADENCE_STAKING_UNSATKED_CLAIM
    get() = CadenceApiManager.getCadenceStakingScript("withdrawUnstaked")
    private set(value) {}

var CADENCE_STAKING_UNSATKED_RESTAKE
    get() = CadenceApiManager.getCadenceStakingScript("restakeUnstaked")
    private set(value) {}

var CADENCE_QUERY_STORAGE_INFO
    get() = CadenceApiManager.getCadenceBasicScript("getStorageInfo")
    private set(value) {}

var CADENCE_QUERY_CHILD_ACCOUNT_META
    get() = CadenceApiManager.getCadenceHybridCustodyScript("getChildAccountMeta")
    private set(value) {}

var CADENCE_QUERY_CHILD_ACCOUNT_LIST
    get() = CadenceApiManager.getCadenceHybridCustodyScript("getChildAccount")
    private set(value) {}

var CADENCE_UNLINK_CHILD_ACCOUNT
    get() = CadenceApiManager.getCadenceHybridCustodyScript("unlinkChildAccount")
    private set(value) {}

var CADENCE_EDIT_CHILD_ACCOUNT
    get() = CadenceApiManager.getCadenceHybridCustodyScript("editChildAccount")
    private set(value) {}

var CADENCE_REVOKE_ACCOUNT_KEY
    get() = CadenceApiManager.getCadenceBasicScript("revokeKey")
    private set(value) {}

var CADENCE_QUERY_CHILD_ACCOUNT_NFT
    get() = CadenceApiManager.getCadenceHybridCustodyScript("getAccessibleCollectionAndIdsDisplay")
    private set(value) {}

var CADENCE_QUERY_CHILD_ACCOUNT_TOKENS
    get() = CadenceApiManager.getCadenceHybridCustodyScript("getAccessibleCoinInfo")
    private set(value) {}

var CADENCE_QUERY_CHILD_ACCOUNT_NFT_COLLECTIONS
    get() = CadenceApiManager.getCadenceHybridCustodyScript("getChildAccountAllowTypes")
    private set(value) {}

var CADENCE_QUERY_MIN_FLOW_BALANCE
    get() = CadenceApiManager.getCadenceBasicScript("getAccountMinFlow")
    private set(value) {}

var CADENCE_CREATE_COA_ACCOUNT
    get() = CadenceApiManager.getCadenceEVMScript("createCoaEmpty")
    private set(value) {}

var CADENCE_CHECK_COA_LINK
    get() = CadenceApiManager.getCadenceEVMScript("checkCoaLink")
    private set(value) {}

var CADENCE_COA_LINK
    get() = CadenceApiManager.getCadenceEVMScript("coaLink")
    private set(value) {}

var CADENCE_QUERY_COA_EVM_ADDRESS
    get() = CadenceApiManager.getCadenceEVMScript("getCoaAddr")
    private set(value) {}

var CADENCE_QUERY_COA_FLOW_BALANCE
    get() = CadenceApiManager.getCadenceEVMScript("getCoaBalance")
    private set(value) {}

var CADENCE_FUND_COA_FLOW_BALANCE
    get() = CadenceApiManager.getCadenceEVMScript("fundCoa")
    private set(value) {}

var CADENCE_WITHDRAW_COA_FLOW_BALANCE
    get() = CadenceApiManager.getCadenceEVMScript("withdrawCoa")
    private set(value) {}

var CADENCE_TRANSFER_FLOW_TO_EVM
    get() = CadenceApiManager.getCadenceEVMScript("transferFlowToEvmAddress")
    private set(value) {}

var CADENCE_CALL_EVM_CONTRACT
    get() = CadenceApiManager.getCadenceEVMScript("callContract")
    private set(value) {}

var CADENCE_BRIDGE_FT_TO_EVM
    get() = CadenceApiManager.getCadenceBridgeScript("bridgeTokensToEvmV2")
    private set(value) {}

var CADENCE_BRIDGE_FT_FROM_FLOW_TO_EVM
    get() = CadenceApiManager.getCadenceBridgeScript("bridgeTokensToEvmAddressV2")
    private set(value) {}

var CADENCE_BRIDGE_FT_FROM_EVM
    get() = CadenceApiManager.getCadenceBridgeScript("bridgeTokensFromEvmV2")
    private set(value) {}

var CADENCE_BRIDGE_FT_FROM_EVM_TO_FLOW
    get() = CadenceApiManager.getCadenceBridgeScript("bridgeTokensFromEvmToFlowV2")
    private set(value) {}

var CADENCE_BRIDGE_NFT_TO_EVM
    get() = CadenceApiManager.getCadenceBridgeScript("bridgeNFTToEvmV2")
    private set(value) {}

var CADENCE_BRIDGE_NFT_FROM_EVM
    get() = CadenceApiManager.getCadenceBridgeScript("bridgeNFTFromEvmV2")
    private set(value) {}

var CADENCE_BRIDGE_NFT_LIST_TO_EVM
    get() = CadenceApiManager.getCadenceBridgeScript("batchBridgeNFTToEvmV2")
    private set(value) {}

var CADENCE_BRIDGE_NFT_LIST_FROM_EVM
    get() = CadenceApiManager.getCadenceBridgeScript("batchBridgeNFTFromEvmV2")
    private set(value) {}

var CADENCE_BRIDGE_NFT_FROM_FLOW_TO_EVM
    get() = CadenceApiManager.getCadenceBridgeScript("bridgeNFTToEvmAddressV2")
    private set(value) {}

var CADENCE_BRIDGE_NFT_FROM_EVM_TO_FLOW
    get() = CadenceApiManager.getCadenceBridgeScript("bridgeNFTFromEvmToFlowV2")
    private set(value) {}

var CADENCE_QUERY_FLOW_BALANCE
    get() = CadenceApiManager.getCadenceBasicScript("queryFlowBalance")
    private set(value) {}

var CADENCE_BRIDGE_CHILD_NFT_TO_EVM
    get() = CadenceApiManager.getCadenceHybridCustodyScript("bridgeChildNFTToEvm")
    private set(value) {}

var CADENCE_BRIDGE_CHILD_NFT_FROM_EVM
    get() = CadenceApiManager.getCadenceHybridCustodyScript("bridgeChildNFTFromEvm")
    private set(value) {}

var CADENCE_BRIDGE_CHILD_NFT_LIST_TO_EVM
    get() = CadenceApiManager.getCadenceHybridCustodyScript("batchBridgeChildNFTToEvm")
    private set(value) {}

var CADENCE_BRIDGE_CHILD_NFT_LIST_FROM_EVM
    get() = CadenceApiManager.getCadenceHybridCustodyScript("batchBridgeChildNFTFromEvm")
    private set(value) {}

var CADENCE_MOVE_NFT_FROM_CHILD_TO_PARENT
    get() = CadenceApiManager.getCadenceHybridCustodyScript("transferChildNFT")
    private set(value) {}

var CADENCE_SEND_NFT_FROM_CHILD_TO_FLOW
    get() = CadenceApiManager.getCadenceHybridCustodyScript("sendChildNFT")
    private set(value) {}

var CADENCE_SEND_NFT_FROM_CHILD_TO_CHILD
    get() = CadenceApiManager.getCadenceHybridCustodyScript("sendChildNFTToChild")
    private set(value) {}

var CADENCE_SEND_NFT_LIST_FROM_CHILD_TO_CHILD
    get() = CadenceApiManager.getCadenceHybridCustodyScript("batchSendChildNFTToChild")
    private set(value) {}

var CADENCE_SEND_NFT_FROM_PARENT_TO_CHILD
    get() = CadenceApiManager.getCadenceHybridCustodyScript("transferNFTToChild")
    private set(value) {}

var CADENCE_SEND_NFT_LIST_FROM_PARENT_TO_CHILD
    get() = CadenceApiManager.getCadenceHybridCustodyScript("batchTransferNFTToChild")
    private set(value) {}

var CADENCE_MOVE_NFT_LIST_FROM_CHILD_TO_PARENT
    get() = CadenceApiManager.getCadenceHybridCustodyScript("batchTransferChildNFT")
    private set(value) {}

var CADENCE_MOVE_FT_FROM_CHILD_TO_PARENT
    get() = CadenceApiManager.getCadenceHybridCustodyScript("transferChildFT")
    private set(value) {}

var CADENCE_SEND_FT_FROM_CHILD_TO_FLOW
    get() = CadenceApiManager.getCadenceHybridCustodyScript("sendChildFT")
    private set(value) {}

var CADENCE_CHECK_CHILD_LINKED_VAULT
    get() = CadenceApiManager.getCadenceHybridCustodyScript("checkChildLinkedVaults")
    private set(value) {}
