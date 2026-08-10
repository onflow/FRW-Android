package com.flowfoundation.wallet.manager.emoji

import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.emoji.model.WalletEmojiInfo
import com.flowfoundation.wallet.utils.uiScope
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList


object AccountEmojiManager {

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

    @Synchronized
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
        return if (filterEmojiList.isEmpty()) getEmojiList().random() else filterEmojiList.random()
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
        uiScope {
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.onEmojiUpdate(userName, address, emojiId, emojiName) }
        }
    }

    /**
     * Get or assign an emoji for [address] within a specific account's own emoji list.
     * Used for non-current accounts to avoid polluting the current account's emoji state.
     *
     * [emojiList] is mutated in-place when a new address is encountered and the result
     * is persisted to the correct account via [AccountManager.updateWalletEmojiInfo].
     */
    @Synchronized
    fun getEmojiByAddressForAccount(
        address: String,
        username: String,
        emojiList: MutableList<WalletEmojiInfo>
    ): WalletEmojiInfo {
        val existing = emojiList.firstOrNull { it.address == address }
        if (existing != null) return existing

        val usedIds = emojiList.map { it.emojiId }
        val available = getEmojiList().filter { it.id !in usedIds }
        val emoji = if (available.isEmpty()) getEmojiList().random() else available.random()
        val info = WalletEmojiInfo(address, emoji.id, emoji.defaultName)
        emojiList.add(info)
        AccountManager.updateWalletEmojiInfo(username, emojiList.toMutableList())
        return info
    }

    /**
     * Remove emoji entries whose address is not in [validAddresses].
     * This prevents stale entries from exhausting the 12-emoji pool.
     */
    @Synchronized
    fun cleanStaleEntries(validAddresses: Set<String>) {
        val before = accountEmojiList.size
        accountEmojiList.removeAll { it.address !in validAddresses }
        if (accountEmojiList.size != before) {
            val username = AccountManager.userInfo()?.username ?: return
            AccountManager.updateWalletEmojiInfo(username, accountEmojiList.toMutableList())
        }
    }

    /**
     * Remove emoji entries whose address is not in [validAddresses] from the given per-account list.
     */
    @Synchronized
    fun cleanStaleEntriesForAccount(
        validAddresses: Set<String>,
        username: String,
        emojiList: MutableList<WalletEmojiInfo>
    ) {
        val before = emojiList.size
        emojiList.removeAll { it.address !in validAddresses }
        if (emojiList.size != before) {
            AccountManager.updateWalletEmojiInfo(username, emojiList.toMutableList())
        }
    }

    fun clear() {
        accountEmojiList.clear()
        listeners.clear()
    }
}

interface OnEmojiUpdate {
    fun onEmojiUpdate(userName: String, address: String, emojiId: Int, emojiName: String)
}
