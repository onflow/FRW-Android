package com.flowfoundation.wallet.page.dialog.processing.fcl

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.transition.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.DialogFclAuthzBinding
import com.flowfoundation.wallet.manager.config.isGasFree
import com.flowfoundation.wallet.manager.transaction.OnTransactionStateChange
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.manager.transaction.TransactionStateManager
import com.flowfoundation.wallet.page.browser.loadFavicon
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.uiScope

class FclTransactionProcessingDialog : BottomSheetDialogFragment(), OnTransactionStateChange {

    private val txId by lazy { arguments?.getString(EXTRA_TX_ID)!! }

    private lateinit var binding: DialogFclAuthzBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogFclAuthzBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        TransactionStateManager.addOnTransactionStateChange(this)
        with(binding) {
            actionButton.setVisible(false)
            uiScope { feeNumber.text = if (isGasFree()) "0" else "0.001" }
            scriptHeaderWrapper.setOnClickListener { toggleScriptVisible() }
            updateState()
        }
    }

    override fun onTransactionStateChange() {
        updateState()
    }

    private fun updateState() {
        val state = TransactionStateManager.getTransactionStateById(txId) ?: return
        if (state.type != TransactionState.TYPE_FCL_TRANSACTION) {
            return
        }
        val data = state.fclTransactionData()
        with(binding) {
            iconView.loadFavicon(data.url?.toFavIcon())
            nameView.text = data.title
            scriptTextView.text = data.voucher.cadence?.trimIndent()
            updateProcessing(state)
        }
    }

    private fun updateProcessing(state: TransactionState) {
        with(binding.progressText) {
            setVisible()
            var textColor = R.color.accent_gray
            var bgColor = R.color.salmon5
            var text = R.string.pending
            when {
                state.isSuccess() -> {
                    textColor = R.color.success3
                    bgColor = R.color.success5
                    text = R.string.success
                }
                state.isFailed() -> {
                    textColor = R.color.warning2
                    bgColor = R.color.warning5
                    text = R.string.failed
                }
                else -> {}
            }

            setText(text)
            backgroundTintList = ColorStateList.valueOf(bgColor.res2color())
            setTextColor(textColor.res2color())
        }
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
        private const val EXTRA_TX_ID = "extra_tx_id"

        fun show(txId: String) {
            val activity = BaseActivity.getCurrentActivity() ?: return
            FclTransactionProcessingDialog().apply {
                arguments = Bundle().apply { putString(EXTRA_TX_ID, txId) }
            }.show(activity.supportFragmentManager, "")
        }
    }
}