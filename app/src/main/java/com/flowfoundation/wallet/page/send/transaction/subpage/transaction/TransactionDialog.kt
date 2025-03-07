package com.flowfoundation.wallet.page.send.transaction.subpage.transaction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogSendConfirmBinding
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.TransactionModel
import com.flowfoundation.wallet.page.send.transaction.subpage.transaction.model.TransactionDialogModel
import com.flowfoundation.wallet.page.send.transaction.subpage.transaction.presenter.TransactionPresenter

class TransactionDialog : BottomSheetDialogFragment() {

    private val transaction by lazy { arguments?.getParcelable<TransactionModel>(EXTRA_TRANSACTION)!! }

    private lateinit var binding: DialogSendConfirmBinding

    private lateinit var presenter: TransactionPresenter
    private lateinit var viewModel: TransactionViewModel


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSendConfirmBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter = TransactionPresenter(this, binding)
        viewModel = ViewModelProvider(this)[TransactionViewModel::class.java].apply {
            bindTransaction(this@TransactionDialog.transaction)
            userInfoLiveData.observe(this@TransactionDialog) { presenter.bind(TransactionDialogModel(userInfo = it)) }
            amountConvertLiveData.observe(this@TransactionDialog) { presenter.bind(TransactionDialogModel(amountConvert = it)) }
            resultLiveData.observe(this@TransactionDialog) { isSuccess ->
                presenter.bind(TransactionDialogModel(isSendSuccess = isSuccess))
                if (!isSuccess) {
                    Toast.makeText(requireContext(), R.string.common_error_hint, Toast.LENGTH_LONG).show()
                    dismiss()
                }
            }
            load()
        }

        binding.closeButton.setOnClickListener { dismiss() }
    }

    companion object {
        private const val EXTRA_TRANSACTION = "extra_transaction"

        fun newInstance(transaction: TransactionModel): TransactionDialog {
            return TransactionDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_TRANSACTION, transaction)
                }
            }
        }
    }
}