package com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.model

class WalletRestoreMnemonicModel(
    val mnemonicList: List<String>? = null,
    val selectSuggest: String? = null,
    val invalidWordList: List<Pair<Int, String>>? = null,
)