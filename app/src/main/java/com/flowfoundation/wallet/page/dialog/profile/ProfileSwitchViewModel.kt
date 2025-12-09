package com.flowfoundation.wallet.page.dialog.profile

import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAllFlowBalance
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.wallet.model.AvatarData
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
import com.flowfoundation.wallet.manager.walletdata.EOAWallet
import com.flowfoundation.wallet.manager.walletdata.ChildWallet
import com.flowfoundation.wallet.manager.walletdata.COAWallet
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

// Data class to represent a profile item with all its data
data class ProfileItemData(
    @SerializedName("account")
    val account: Account,
    @SerializedName("avatarList")
    val avatarList: List<AvatarData>,
    @SerializedName("balanceMap")
    val balanceMap: Map<String, String>
)

// Sealed class for different item types in the switch list
sealed class SwitchItemData {
    data class ProfileItem(
        @SerializedName("data")
        val data: ProfileItemData
    ) : SwitchItemData()
    data class LocalSwitchItem(
        @SerializedName("account")
        val account: LocalSwitchAccount
    ) : SwitchItemData()
}

class ProfileSwitchViewModel : ViewModel() {

    private val _switchItemList = MutableStateFlow<List<SwitchItemData>>(emptyList())
    val switchItemList: StateFlow<List<SwitchItemData>> = _switchItemList.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val service by lazy { retrofitApi().create(ApiService::class.java) }

    // Cache for verified COA addresses and their avatar data per profile
    private val verifiedCoaAvatarsMap = mutableMapOf<String, MutableMap<String, AvatarData>>()

    fun loadSwitchAccountList() {
        _isLoading.value = true
        ioScope {
            val rawList = AccountManager.getSwitchAccountList()

            // 1. Fast Path: Build initial list
            val initialList = rawList.mapNotNull { item ->
                when (item) {
                    is Account -> {
                        val profileData = buildInitialProfileData(item)
                        SwitchItemData.ProfileItem(profileData)
                    }
                    is LocalSwitchAccount -> SwitchItemData.LocalSwitchItem(item)
                    else -> null
                }
            }

            _switchItemList.value = initialList
            _isLoading.value = false

            // 2. Slow Path: Fetch balances and update
            val updatedList = initialList.map { item ->
                if (item is SwitchItemData.ProfileItem) {
                    val updatedData = fetchBalancesAndCoaStatus(item.data)
                    SwitchItemData.ProfileItem(updatedData)
                } else {
                    item
                }
            }
            _switchItemList.value = updatedList
        }
    }

    private fun getFlowWalletsForCurrentNetwork(profile: Account): List<FlowWallet> {
        val currentNetwork = chainNetWorkString()
        return profile.walletNodes.filterIsInstance<FlowWallet>().filter { it.chainIdString == currentNetwork }
    }

    private fun buildInitialProfileData(profile: Account): ProfileItemData {
        val flowWallets = getFlowWalletsForCurrentNetwork(profile)
        val profileId = profile.wallet?.id
        val avatars = mutableListOf<AvatarData>()

        flowWallets.forEach { flowWallet ->
            // Main account
            val emojiInfo = AccountEmojiManager.getEmojiByAddress(flowWallet.address)
            avatars.add(AvatarData.Emoji(emojiInfo.emojiId))

            flowWallet.linkedWallets.forEach { linked ->
                when (linked) {
                    is ChildWallet -> {
                         if (linked.icon.isNotEmpty()) {
                            avatars.add(AvatarData.Icon(linked.icon))
                        } else {
                            val childEmojiInfo = AccountEmojiManager.getEmojiByAddress(linked.address)
                            avatars.add(AvatarData.Emoji(childEmojiInfo.emojiId))
                        }
                    }
                    is COAWallet -> {
                         // Check cached COA
                        val coaAddress = linked.address
                        if (profileId != null && profileId in verifiedCoaAvatarsMap && coaAddress in verifiedCoaAvatarsMap[profileId]!!) {
                             avatars.add(verifiedCoaAvatarsMap[profileId]!![coaAddress]!!)
                        }
                    }
                }
            }
        }

        // Add EOA address avatar
        profile.walletNodes.filterIsInstance<EOAWallet>().forEach { eoa ->
            val emojiInfo = AccountEmojiManager.getEmojiByAddress(eoa.address)
            avatars.add(AvatarData.Emoji(emojiInfo.emojiId))
        }

        return ProfileItemData(profile, avatars, emptyMap())
    }

    private suspend fun fetchBalancesAndCoaStatus(data: ProfileItemData): ProfileItemData {
        val profile = data.account
        val flowWallets = getFlowWalletsForCurrentNetwork(profile)

        val profileId = profile.wallet?.id ?: return data

        val addressListToQuery = mutableListOf<String>()
        val currentAvatars = data.avatarList.toMutableList()

        // 1. Collect addresses
        flowWallets.forEach { flowWallet ->
            addressListToQuery.add(flowWallet.address)

            flowWallet.linkedWallets.forEach { linked ->
                addressListToQuery.add(linked.address)
            }
        }

        // Add EOA Address
        profile.walletNodes.filterIsInstance<EOAWallet>().forEach { eoa ->
             addressListToQuery.add(eoa.address)
        }

        if (addressListToQuery.isEmpty()) return data

        // 2. Fetch balances for ALL addresses at once
        val balanceMap = cadenceGetAllFlowBalance(addressListToQuery) ?: emptyMap()
        val formattedBalanceMap = balanceMap.mapValues { (_, balance) ->
            "${balance.formatLargeBalanceNumber(isAbbreviation = true)} FLOW"
        }

        // 3. Process COA avatars based on fetched balances and NFT status
        if (profileId !in verifiedCoaAvatarsMap) {
            verifiedCoaAvatarsMap[profileId] = mutableMapOf()
        }
        val verifiedCoaAvatars = verifiedCoaAvatarsMap[profileId]!!

        flowWallets.forEach { flowWallet ->
            flowWallet.linkedWallets.filterIsInstance<COAWallet>().forEach { coaWallet ->
                val coaAddress = coaWallet.address

                val coaBalance = balanceMap[coaAddress]
                val hasBalance = coaBalance != null && coaBalance > BigDecimal.ZERO
                var hasNFTs = false

                if (!hasBalance) {
                    try {
                        val nftResponse = service.getEVMNFTCollections(coaAddress)
                        val totalNftCount = nftResponse.data?.sumOf { it.count ?: 0 } ?: 0
                        hasNFTs = nftResponse.data?.isNotEmpty() == true && totalNftCount > 0
                    } catch (e: Exception) {
                        // Ignore NFT API errors
                    }
                }

                if (hasBalance || hasNFTs) {
                    // Add if not present
                    if (!verifiedCoaAvatars.containsKey(coaAddress)) {
                        val emojiInfo = AccountEmojiManager.getEmojiByAddress(coaAddress)
                        val avatarData = AvatarData.Emoji(emojiInfo.emojiId)
                        verifiedCoaAvatars[coaAddress] = avatarData
                        currentAvatars.add(avatarData)
                    }
                } else {
                    // Remove if present (e.g., balance went to 0)
                    if (verifiedCoaAvatars.containsKey(coaAddress)) {
                        val avatarToRemove = verifiedCoaAvatars[coaAddress]
                        verifiedCoaAvatars.remove(coaAddress)
                        currentAvatars.remove(avatarToRemove)
                    }
                }
            }
        }

        return ProfileItemData(profile, currentAvatars, formattedBalanceMap)
    }
}
