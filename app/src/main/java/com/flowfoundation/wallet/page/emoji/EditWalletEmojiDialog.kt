package com.flowfoundation.wallet.page.emoji

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.emoji.model.WalletEmojiInfo

class EditWalletEmojiDialog(
    private val context: Context,
    private val username: String,
    private val walletEmojiInfo: WalletEmojiInfo
) {
    fun show() {
        var dialog: Dialog? = null
        with(AlertDialog.Builder(context, R.style.Theme_AlertDialogTheme)) {
            setView(EditWalletEmojiDialogView(context, username, walletEmojiInfo) { dialog?.cancel() })
            with(create()) {
                dialog = this
                show()
            }
        }
    }
}

@SuppressLint("ViewConstructor")
class EditWalletEmojiDialogView(
    context: Context,
    username: String,
    walletEmojiInfo: WalletEmojiInfo,
    private val onCancel: () -> Unit,
) : FrameLayout(context) {

    private val iconView by lazy { findViewById<TextView>(R.id.tv_account_icon) }
    private val emojiListView by lazy { findViewById<RecyclerView>(R.id.rv_emoji_list) }
    private val etEmojiName by lazy { findViewById<EditText>(R.id.et_emoji_name) }
    private val cancelButton by lazy { findViewById<View>(R.id.cancel_button) }
    private val saveButton by lazy { findViewById<View>(R.id.save_button) }

    private var selectedEmojiId = walletEmojiInfo.emojiId

    init {
        LayoutInflater.from(context).inflate(R.layout.dialog_edit_wallet_emoji, this)
        val emojiList = listOf(
            Emoji.KOALA,
            Emoji.LION,
            Emoji.PANDA,
            Emoji.BUTTERFLY,
            Emoji.DRAGON,
            Emoji.PENGUIN,
            Emoji.CHERRY,
            Emoji.EMPTY,
            Emoji.CHESTNUT,
            Emoji.PEACH,
            Emoji.LEMON,
            Emoji.COCONUT,
            Emoji.AVOCADO
        )
        val selectPosition = emojiList.indexOfFirst { walletEmojiInfo.emojiId == it.id }
        initIconWithId(walletEmojiInfo.emojiId)
        etEmojiName.setText(walletEmojiInfo.emojiName)
        etEmojiName.doOnTextChanged { text, _, _, _ ->
            saveButton.isEnabled = !(text.isNullOrEmpty())
        }
        val listAdapter = EmojiListAdapter(selectPosition) { selectedId ->
            selectedEmojiId = selectedId
            initIconWithId(selectedId)
        }
        with(emojiListView) {
            layoutManager = GridLayoutManager(getContext(), 7)
            adapter = listAdapter
        }

        listAdapter.setNewDiffData(emojiList)
        cancelButton.setOnClickListener { onCancel() }
        saveButton.setOnClickListener {
            hideKeyboard()
            val emojiName = etEmojiName.text.toString().trim()
            AccountEmojiManager.changeEmojiInfo(username, walletEmojiInfo.address,
                selectedEmojiId, emojiName)
            onCancel()
        }
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etEmojiName.windowToken, 0)
    }

    private fun initIconWithId(emojiId: Int) {
        iconView.text = Emoji.getEmojiById(emojiId)
        iconView.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(emojiId))
    }
}