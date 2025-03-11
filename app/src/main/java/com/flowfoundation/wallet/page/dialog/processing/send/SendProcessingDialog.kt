package com.flowfoundation.wallet.page.dialog.processing.send

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.databinding.DialogSendConfirmBinding
import com.flowfoundation.wallet.manager.transaction.TransactionState
import com.flowfoundation.wallet.page.dialog.processing.send.model.SendProcessingDialogModel
import com.flowfoundation.wallet.page.dialog.processing.send.presenter.SendProcessingPresenter
import com.flowfoundation.wallet.utils.uiScope

class SendProcessingDialog : BottomSheetDialogFragment() {

    private val state by lazy { arguments?.getParcelable<TransactionState>(EXTRA_STATE)!! }

    private lateinit var binding: DialogSendConfirmBinding

    private lateinit var presenter: SendProcessingPresenter
    private lateinit var viewModel: SendProcessingViewModel


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSendConfirmBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter = SendProcessingPresenter(binding, state)
        viewModel = ViewModelProvider(this)[SendProcessingViewModel::class.java].apply {
            bindTransactionState(this@SendProcessingDialog.state)
            userInfoLiveData.observe(this@SendProcessingDialog) { presenter.bind(SendProcessingDialogModel(userInfo = it)) }
            amountConvertLiveData.observe(this@SendProcessingDialog) { presenter.bind(SendProcessingDialogModel(amountConvert = it)) }
            stateChangeLiveData.observe(this@SendProcessingDialog) { presenter.bind(SendProcessingDialogModel(stateChange = it)) }
            load()
        }

        binding.closeButton.setOnClickListener { dismiss() }
    }

    companion object {
        private const val EXTRA_STATE = "EXTRA_STATE"

        private fun newInstance(state: TransactionState): SendProcessingDialog {
            return SendProcessingDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_STATE, state)
                }
            }
        }

        fun show(state: TransactionState) {
            uiScope {
                val activity = BaseActivity.getCurrentActivity() ?: return@uiScope
                newInstance(state).show(activity.supportFragmentManager, "")
            }
        }
    }
}