package com.flowfoundation.wallet.widgets.webview.evm.dialog

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.flowfoundation.wallet.databinding.DialogEvmAccountBinding
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.page.browser.loadFavicon
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.utils.extensions.capitalizeV2
import com.flowfoundation.wallet.utils.extensions.urlHost
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMDialogModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class EvmRequestAccountDialog : BottomSheetDialogFragment() {

    private var data: EVMDialogModel? = null
    private var result: Continuation<Boolean>? = null
    private lateinit var binding: DialogEvmAccountBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogEvmAccountBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        result?: return
        val data = data ?: return
        val address = EVMWalletManager.getEVMAddress()
        val emojiInfo = AccountEmojiManager.getEmojiByAddress(address)
        with(binding) {
            ivIcon.loadFavicon(data.logo ?: data.url?.toFavIcon())
            tvName.text = data.title?.split(" ")?.last()
            tvUrl.text = data.url?.urlHost()
            tvWalletAddress.text = address
            tvWalletIcon.text = Emoji.getEmojiById(emojiInfo.emojiId)
            tvWalletIcon.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(emojiInfo.emojiId))
            tvWalletTitle.text = emojiInfo.emojiName
            tvNetwork.text = chainNetWorkString().capitalizeV2()
            btnCancel.setOnClickListener {
                result?.resume(false)
                dismiss()
            }
            btnConnect.setOnClickListener {
                result?.resume(true)
                dismiss()
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        result?.resume(false)
    }

    suspend fun show(
        fragmentManager: FragmentManager,
        data: EVMDialogModel,
    ) = suspendCoroutine { result ->
        this.result = result
        this.data = data
        show(fragmentManager, "")
    }

    override fun onResume() {
        if (result == null) {
            dismiss()
        }
        super.onResume()
    }
}