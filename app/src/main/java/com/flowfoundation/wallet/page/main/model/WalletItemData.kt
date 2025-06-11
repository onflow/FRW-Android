package com.flowfoundation.wallet.page.main.model


data class WalletAccountData(
    val address: String,
    val name: String,
    val emojiId: Int,
    val isSelected: Boolean,
    val linkedAccounts: List<LinkedAccountData> = emptyList()
)

data class LinkedAccountData(
    val address: String,
    val name: String,
    val emojiId: Int,
    val isSelected: Boolean,
    val isEVMAccount: Boolean
)
