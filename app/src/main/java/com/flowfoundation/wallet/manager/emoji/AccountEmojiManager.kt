package com.flowfoundation.wallet.manager.emoji

import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.emoji.model.WalletEmojiInfo
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList


object AccountEmojiManager {
    private val TAG = AccountEmojiManager::class.java.simpleName

    private val listeners = CopyOnWriteArrayList<WeakReference<OnEmojiUpdate>>()
    private val accountEmojiList = mutableListOf<WalletEmojiInfo>()

    fun init() {
        accountEmojiList.clear()
        val list = AccountManager.emojiInfoList()
        list?.let {
            accountEmojiList.addAll(it)
        }
    }

    private fun getEmojiList(): List<Emoji> {
        return listOf(
            Emoji.KOALA,
            Emoji.LION,
            Emoji.PANDA,
            Emoji.BUTTERFLY,
            Emoji.DRAGON,
            Emoji.PENGUIN,
            Emoji.CHERRY,
            Emoji.CHESTNUT,
            Emoji.PEACH,
            Emoji.LEMON,
            Emoji.COCONUT,
            Emoji.AVOCADO
        )
    }

    fun getEmojiByAddress(address: String?): WalletEmojiInfo {
        val currentUserName = AccountManager.userInfo()?.username
        val randomEmoji = getRandomEmoji(currentUserName, address)
        if (address == null) {
            return WalletEmojiInfo(
                "",
                randomEmoji.id,
                randomEmoji.defaultName
            )
        }
        if (currentUserName == null) {
            return WalletEmojiInfo(
                address,
                randomEmoji.id,
                randomEmoji.defaultName
            )
        }
        val walletEmoji = accountEmojiList.firstOrNull {
            it.address == address
        }
        if (walletEmoji == null) {
            val emojiInfo = WalletEmojiInfo(
                address,
                randomEmoji.id,
                randomEmoji.defaultName
            )
            accountEmojiList.add(emojiInfo)
            AccountManager.updateWalletEmojiInfo(currentUserName, accountEmojiList.toMutableList())
            return emojiInfo
        } else {
            return WalletEmojiInfo(
                address,
                walletEmoji.emojiId,
                walletEmoji.emojiName
            )
        }
    }

    private fun getRandomEmoji(username: String?, address: String?): Emoji {
        if (username == null || address == null) {
            return Emoji.PEACH
        }
        val idList = accountEmojiList.map { it.emojiId }
        val filterEmojiList = getEmojiList().filter { emoji ->
            emoji.id !in idList
        }
        return if(filterEmojiList.isEmpty()) Emoji.PENGUIN else filterEmojiList.random()
    }

    fun changeEmojiInfo(userName: String, address: String, emojiId: Int, emojiName: String) {
        accountEmojiList.removeAll {
            it.address == address
        }
        accountEmojiList.add(
            WalletEmojiInfo(
                address,
                emojiId,
                emojiName
            )
        )
        dispatchListeners(userName, address, emojiId, emojiName)
        AccountManager.updateWalletEmojiInfo(userName, accountEmojiList.toMutableList())
    }

    fun addListener(callback: OnEmojiUpdate) {
        if (listeners.firstOrNull { it.get() == callback } != null) {
            return
        }
        uiScope {
            this.listeners.add(WeakReference(callback))
        }
    }

    private fun dispatchListeners(
        userName: String,
        address: String,
        emojiId: Int,
        emojiName: String
    ) {
        logd(TAG, "dispatchListeners $address:$emojiId:$emojiName")
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onEmojiUpdate(userName, address, emojiId, emojiName) }
        }
    }
}

interface OnEmojiUpdate {
    fun onEmojiUpdate(userName: String, address: String, emojiId: Int, emojiName: String)
}
