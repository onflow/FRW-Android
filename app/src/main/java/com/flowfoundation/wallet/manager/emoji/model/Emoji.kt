package com.flowfoundation.wallet.manager.emoji.model

import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.res2color


enum class Emoji(val id: Int, val emoji: String, val defaultName: String, val colorRes: Int) {
    EMPTY(-1, "", "", R.color.transparent),
    KOALA(0, "\uD83D\uDC28", "Koala", R.color.emoji_avocado),
    LION(1, "\uD83E\uDD81", "Lion", R.color.emoji_lion),
    PANDA(2, "\uD83D\uDC3C", "Panda", R.color.emoji_panda),
    BUTTERFLY(3, "\uD83E\uDD8B", "Butterfly", R.color.emoji_butterfly),
    DRAGON(4, "\uD83D\uDC32", "Dragon", R.color.emoji_dragon),
    PENGUIN(5, "\uD83D\uDC27", "Penguin", R.color.emoji_penguin),
    CHERRY(6, "\uD83C\uDF52", "Cherry", R.color.emoji_cherry),
    CHESTNUT(7, "\uD83C\uDF30", "Chestnut", R.color.emoji_chestnut),
    PEACH(8, "\uD83C\uDF51", "Peach", R.color.emoji_peach),
    LEMON(9, "\uD83C\uDF4B", "Lemon", R.color.emoji_lemon),
    COCONUT(10, "\uD83E\uDD65", "Coconut", R.color.emoji_coconut),
    AVOCADO(11, "\uD83E\uDD51", "Avocado", R.color.emoji_avocado);

    companion object {

        @JvmStatic
        fun getEmojiById(id: Int): String {
            return Emoji.values().firstOrNull { it.id == id }?.emoji ?: PEACH.emoji
        }

        @JvmStatic
        fun getEmojiColorRes(id: Int): Int {
            return Emoji.values().firstOrNull { it.id == id }?.colorRes?.res2color() ?: R.color.transparent.res2color()
        }
    }
}
