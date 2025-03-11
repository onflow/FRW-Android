package com.flowfoundation.wallet.widgets.webview.evm.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.Scene
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogEvmTransactionBinding
import com.flowfoundation.wallet.page.browser.loadFavicon
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMDialogModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nftco.flow.sdk.hexToBytes


class EVMSendTransactionDialog: BottomSheetDialogFragment() {

    private val data by lazy { arguments?.getParcelable<EVMDialogModel>(EXTRA_DATA) }

    private lateinit var binding: DialogEvmTransactionBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogEvmTransactionBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (data == null) {
            dismiss()
            return
        }
        val data = data ?: return
        with(binding) {
            title1.text = R.string.transaction_to.res2String()
            iconView.loadFavicon(data.logo ?: data.url?.toFavIcon())
            nameView.text = data.title
            try {
                data.cadence?.hexToBytes()?.let { scriptTextView.text = it.toString(Charsets.UTF_8) }
            } catch (e: Exception) {
                scriptTextView.text = data.cadence
            }
            actionButton.setOnProcessing {
                approveCallback?.invoke(true)
                dismiss()
            }
            scriptHeaderWrapper.setOnClickListener { toggleScriptVisible() }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        approveCallback?.invoke(false)
    }

    override fun onDestroy() {
        approveCallback = null
        super.onDestroy()
    }

    private fun toggleScriptVisible() {
        with(binding) {
            TransitionManager.go(Scene(scriptLayout), TransitionSet().apply {
                addTransition(ChangeBounds().apply { duration = 150 })
                addTransition(Fade(Fade.IN).apply { duration = 150 })
            })
            val toVisible = !scriptTextWrapper.isVisible()
            scriptTextWrapper.setVisible(toVisible)
            scriptArrow.rotation = if (toVisible) 0f else 270f
        }
    }

    companion object {
        private const val EXTRA_DATA = "data"

        private var approveCallback: ((isApprove: Boolean) -> Unit)? = null

        fun observe(callback: (isApprove: Boolean) -> Unit) {
            this.approveCallback = callback
        }

        fun show(
            fragmentManager: FragmentManager,
            data: EVMDialogModel,
        ) {
            EVMSendTransactionDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_DATA, data)
                }
            }.show(fragmentManager, "")
        }
    }
}