package com.flowfoundation.wallet.page.dialog.profile

import androidx.lifecycle.ViewModel
import com.flowfoundation.wallet.manager.account.Account
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.account.model.LocalSwitchAccount
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceGetAllFlowBalance
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.wallet.model.AvatarData
import com.flowfoundation.wallet.utils.formatLargeBalanceNumber
import com.flowfoundation.wallet.utils.ioScope
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
        ioScope {
            val rawList = AccountManager.getSwitchAccountList()
            val processedList = mutableListOf<SwitchItemData>()

            rawList.forEach { item ->
                when (item) {
                    is Account -> {
                        val profileData = fetchProfileData(item)
                        processedList.add(SwitchItemData.ProfileItem(profileData))
                    }
                    is LocalSwitchAccount -> {
                        processedList.add(SwitchItemData.LocalSwitchItem(item))
                    }
                }
            }

            _switchItemList.value = processedList
            _isLoading.value = false
        }
    }

    private suspend fun fetchProfileData(profile: Account): ProfileItemData {
        val wallet = profile.wallet ?: return ProfileItemData(profile, emptyList(), emptyMap())
        val addressList = mutableListOf<String>()
        val avatars = mutableListOf<AvatarData>()
        val address = wallet.walletAddress() ?: return ProfileItemData(profile, emptyList(), emptyMap())
        val profileId = wallet.id

        // Initialize cache for this profile if not exists
        if (profileId !in verifiedCoaAvatarsMap) {
            verifiedCoaAvatarsMap[profileId] = mutableMapOf()
        }
        val verifiedCoaAvatars = verifiedCoaAvatarsMap[profileId]!!

        val emojiInfo = AccountEmojiManager.getEmojiByAddress(address)

        // Add main account emoji
        avatars.add(AvatarData.Emoji(emojiInfo.emojiId))
        addressList.add(address)

        // Add child accounts
        WalletManager.childAccountList(address)?.get()?.forEach { childAccount ->
            addressList.add(childAccount.address)
            if (childAccount.icon.isNotEmpty()) {
                avatars.add(AvatarData.Icon(childAccount.icon))
            } else {
                val childEmojiInfo = AccountEmojiManager.getEmojiByAddress(childAccount.address)
                avatars.add(AvatarData.Emoji(childEmojiInfo.emojiId))
            }
        }

        var pendingCoaAddress: String? = null
        val coaAddress = profile.evmAddressData?.evmAddressMap?.get(address)
        coaAddress?.let { coa ->
            addressList.add(coa)
            // Add to pending list for verification if not already verified
            if (coa !in verifiedCoaAvatars) {
                pendingCoaAddress = coa
            } else {
                // Add directly to avatars if already verified
                avatars.add(verifiedCoaAvatars[coa]!!)
            }
        }

        // Fetch balances
        val balanceMap = cadenceGetAllFlowBalance(addressList) ?: emptyMap()
        val formattedBalanceMap = balanceMap.mapValues { (_, balance) ->
            "${balance.formatLargeBalanceNumber(isAbbreviation = true)} FLOW"
        }

        // Check if COA address should be added to avatars
        pendingCoaAddress?.let { coaAddress ->
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
                // Add COA address to avatar list
                if (coaAddress !in verifiedCoaAvatars) {
                    val emojiInfo = AccountEmojiManager.getEmojiByAddress(coaAddress)
                    val avatarData = AvatarData.Emoji(emojiInfo.emojiId)
                    verifiedCoaAvatars[coaAddress] = avatarData
                    avatars.add(avatarData)
                }
            } else {
                // Remove COA address from avatar list if it no longer has assets
                if (coaAddress in verifiedCoaAvatars) {
                    val avatarToRemove = verifiedCoaAvatars[coaAddress]
                    verifiedCoaAvatars.remove(coaAddress)
                    avatars.remove(avatarToRemove)
                }
            }
        }

        return ProfileItemData(profile, avatars, formattedBalanceMap)
    }
}
