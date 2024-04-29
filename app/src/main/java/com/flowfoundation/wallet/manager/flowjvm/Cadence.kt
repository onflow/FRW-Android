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

const val CADENCE_CHECK_STAKING_ENABLED = """
    import FlowIDTableStaking from 0xFlowIDTableStaking

    pub fun main():Bool {
      return FlowIDTableStaking.stakingEnabled()
    }
"""

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

const val CADENCE_QUERY_CHILD_ACCOUNT_NFT_ID = """
    import HybridCustody from 0xHybridCustody
    import MetadataViews from 0xMetadataViews
    import FungibleToken from 0xFungibleToken
    import NonFungibleToken from 0xNonFungibleToken
    
    pub struct NFTInfo {
      pub let id: String
      pub let idList: [UInt64]
    
      init(id: String, idList: [UInt64]) {
        self.id = id
        self.idList = idList
      }
    }
    
    pub struct ChildNFTInfo {
      pub let address: Address
      pub let info: [NFTInfo]
    
      init(address: Address, info: [NFTInfo]) {
        self.address = address
        self.info = info
      }
    }
    
    pub fun main(parent: Address, child: Address): ChildNFTInfo {
        let manager = getAuthAccount(parent).borrow<&HybridCustody.Manager>(from: HybridCustody.ManagerStoragePath) ?? panic ("manager does not exist")
    
        var typeIdsWithProvider: {Address: [Type]} = {}
    
        let providerType = Type<Capability<&{NonFungibleToken.Provider}>>()
        let collectionType: Type = Type<@{NonFungibleToken.CollectionPublic}>()
    
        // Iterate through child accounts
    
            let acct = getAuthAccount(child)
            let foundTypes: [Type] = []
            let nfts: [NFTInfo] = []
            let childAcct = manager.borrowAccount(addr: child) ?? panic("child account not found")
            // get all private paths
            acct.forEachPrivate(fun (path: PrivatePath, type: Type): Bool {
                // Check which private paths have NFT Provider AND can be borrowed
                if !type.isSubtype(of: providerType){
                    return true
                }
                if let cap = childAcct.getCapability(path: path, type: Type<&{NonFungibleToken.Provider}>()) {
                    let providerCap = cap as! Capability<&{NonFungibleToken.Provider}> 
    
                    if !providerCap.check(){
                        // if this isn't a provider capability, exit the account iteration function for this path
                        return true
                    }
                    foundTypes.append(cap.borrow<&AnyResource>()!.getType())
                }
                return true
            })
            typeIdsWithProvider[child] = foundTypes
    
            // iterate storage, check if typeIdsWithProvider contains the typeId, if so, add to nfts
            acct.forEachStored(fun (path: StoragePath, type: Type): Bool {
    
                if typeIdsWithProvider[child] == nil {
                    return true
                }
    
                for key in typeIdsWithProvider.keys {
                    for idx, value in typeIdsWithProvider[key]! {
                        let value = typeIdsWithProvider[key]!
    
                        if value[idx] != type {
                            continue
                        } else {
                            if type.isInstance(collectionType) {
                                continue
                            }
                            if let collection = acct.borrow<&{NonFungibleToken.CollectionPublic}>(from: path) { 
                                nfts.append(
                                  NFTInfo(id: type.identifier, idList: collection.getIDs())
                                )
                            }
                            continue
                        }
                    }
                }
                return true
            })
    
        return ChildNFTInfo(address: child, info: nfts)
    }
"""

var CADENCE_CREATE_COA_ACCOUNT
    get() = CadenceApiManager.getCadenceEVMScript("createCoa")
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
