package com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin

class KeyboardItem(
    val type: Int,
    val number: Int? = null,
    val charText: String? = null,
    val actionIcon: Int = 0,
) {
    companion object {
        const val TYPE_TEXT_KEY = 1
        const val TYPE_DELETE_KEY = 2
        const val TYPE_CLEAR_KEY = 3
        const val TYPE_EMPTY_KEY = 4
    }
}