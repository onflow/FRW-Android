package com.flowfoundation.wallet.widgets.webview.fcl.dialog

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.databinding.DialogFclAuthnBinding
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.blocklist.BlockManager
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.browser.loadFavicon
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.utils.extensions.capitalizeV2
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.urlHost
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FclAuthnDialog : BottomSheetDialogFragment() {

    private var data: FclDialogModel? = null
    private var result: Continuation<Boolean>? = null

    private lateinit var binding: DialogFclAuthnBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogFclAuthnBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        result ?: return
        val data = data ?: return
        val address = WalletManager.selectedWalletAddress()
        val emojiInfo = AccountEmojiManager.getEmojiByAddress(address)
        with(binding) {
            iconView.loadFavicon(data.logo ?: data.url?.toFavIcon())
            nameView.text = data.title
            urlView.text = data.url?.urlHost()
            tvWalletAddress.text = address
            tvWalletIcon.text = Emoji.getEmojiById(emojiInfo.emojiId)
            tvWalletIcon.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(emojiInfo.emojiId))
            tvNetwork.text = chainNetWorkString().capitalizeV2()
            cancelButton.setOnClickListener {
                result?.resume(false)
                dismiss()
            }
            approveButton.setOnClickListener {
                result?.resume(true)
                dismiss()
            }

            uiScope {
                if (data.url.isNullOrEmpty()) {
                    return@uiScope
                }
                val isBlockedUrl = BlockManager.isBlocked(data.url)
                flBlockedTip.setVisible(isBlockedUrl)
                approveButton.setVisible(isBlockedUrl.not())
                flBlockedConnect.setVisible(isBlockedUrl)
                flBlockedConnect.setOnClickListener {
                    result?.resume(true)
                    dismiss()
                }
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        result?.resume(false)
    }

    suspend fun show(
        fragmentManager: FragmentManager,
        data: FclDialogModel,
    ) = suspendCoroutine { result ->
        if (checkAndShowNetworkWrongDialog(fragmentManager, data)) {
            result.resume(false)
            return@suspendCoroutine
        }

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