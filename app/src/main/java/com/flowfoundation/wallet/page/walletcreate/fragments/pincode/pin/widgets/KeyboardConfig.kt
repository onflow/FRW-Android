package com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.widgets

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.KeyboardItem

val keyboardT9Normal = listOf(
    listOf(
        KeyboardItem(type = KeyboardItem.TYPE_TEXT_KEY, number = 1),
        KeyboardItem(type = KeyboardItem.TYPE_TEXT_KEY, number = 2, charText = "ABC"),
        KeyboardItem(type = KeyboardItem.TYPE_TEXT_KEY, number = 3, charText = "DEF"),
    ),
    listOf(
        KeyboardItem(type = KeyboardItem.TYPE_TEXT_KEY, number = 4, charText = "GHI"),
        KeyboardItem(type = KeyboardItem.TYPE_TEXT_KEY, number = 5, charText = "JKL"),
        KeyboardItem(type = KeyboardItem.TYPE_TEXT_KEY, number = 6, charText = "MNO"),
    ),
    listOf(
        KeyboardItem(type = KeyboardItem.TYPE_TEXT_KEY, number = 7, charText = "PQRS"),
        KeyboardItem(type = KeyboardItem.TYPE_TEXT_KEY, number = 8, charText = "TUV"),
        KeyboardItem(type = KeyboardItem.TYPE_TEXT_KEY, number = 9, charText = "WXYZ"),
    ),
    listOf(
        KeyboardItem(type = KeyboardItem.TYPE_EMPTY_KEY),
        KeyboardItem(type = KeyboardItem.TYPE_TEXT_KEY, number = 0),
        KeyboardItem(type = KeyboardItem.TYPE_DELETE_KEY, actionIcon = R.drawable.ic_keyboard_delete_24),
    ),
)

fun onKeyActionChange(keyList: MutableList<KeyboardItem>, key: KeyboardItem) {
    when (key.type) {
        KeyboardItem.TYPE_DELETE_KEY -> {
            keyList.removeLastOrNull()
        }
        KeyboardItem.TYPE_CLEAR_KEY -> {
            keyList.clear()
        }
        else -> {
            keyList.add(key)
        }
    }
}

fun getInputStringByKeys(keyList: List<KeyboardItem>): String = keyList.map { it.number ?: it.charText }.joinToString("") { it.toString() }

fun List<KeyboardItem>.toFilter(): List<List<String>> {
    val list = mutableListOf<List<String>>()
    forEach { item ->
        val chars = mutableListOf<String>()
        item.number?.let { chars.add("$it") }
        item.charText?.let { chars.addAll(it.split("")) }
        list.add(chars.filter { it.isNotBlank() }.map { it.uppercase() })
    }
    return list
}