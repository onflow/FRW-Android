package com.flowfoundation.wallet.widgets.webview.evm.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.flowfoundation.wallet.databinding.DialogEvmAccountBinding
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.page.browser.loadFavicon
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.utils.extensions.urlHost
import com.flowfoundation.wallet.widgets.webview.evm.model.EvmDialogModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class EvmRequestAccountDialog : BottomSheetDialogFragment() {

    private var data: EvmDialogModel? = null
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
        with(binding) {
            ivIcon.loadFavicon(data.logo ?: data.url?.toFavIcon())
            tvName.text = data.title?.split(" ")?.last()
            tvUrl.text = data.url?.urlHost()
            tvWalletAddress.text = EVMWalletManager.getEVMAddress()
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
        data: EvmDialogModel,
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