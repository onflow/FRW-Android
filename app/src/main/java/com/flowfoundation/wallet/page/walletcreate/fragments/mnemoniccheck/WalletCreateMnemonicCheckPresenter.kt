package com.flowfoundation.wallet.page.walletcreate.fragments.mnemoniccheck

import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentWalletCreateMnemonicCheckBinding
import com.flowfoundation.wallet.page.walletcreate.WALLET_CREATE_STEP_PIN_GUIDE
import com.flowfoundation.wallet.page.walletcreate.WalletCreateViewModel
import com.flowfoundation.wallet.utils.setBackupManually
import com.flowfoundation.wallet.utils.toast

class WalletCreateMnemonicCheckPresenter(
    private val fragment: Fragment,
    private val binding: FragmentWalletCreateMnemonicCheckBinding,
) : BasePresenter<WalletCreateMnemonicCheckModel> {

    private val pageViewModel by lazy { ViewModelProvider(fragment.requireActivity())[WalletCreateViewModel::class.java] }

    private val viewModel by lazy { ViewModelProvider(fragment)[WalletCreateMnemonicCheckViewModel::class.java] }

    init {
        binding.nextButton.setOnClickListener {
            if (isAllVerified()) {
                setBackupManually()
                pageViewModel.changeStep(WALLET_CREATE_STEP_PIN_GUIDE)
            } else {
                toast(msgRes = R.string.mnemonic_check_fail)
                viewModel.generateMnemonicQuestion()
            }
        }
    }

    override fun bind(model: WalletCreateMnemonicCheckModel) {
        model.questionList?.let { setupQuestion(it) }
    }

    private fun setupQuestion(questions: List<MnemonicQuestionModel>) {
        binding.mnemonicContainer.removeAllViews()
        questions.forEach {
            val itemView = MnemonicQuestionView(fragment.requireContext())
            binding.mnemonicContainer.addView(itemView)
            itemView.bindData(it)
            itemView.setOnButtonCheckedListener { updateState() }
        }
        updateState()
    }

    private fun updateState() {
        binding.nextButton.isEnabled = isAllSelected()
    }

    private fun isAllVerified(): Boolean {
        binding.mnemonicContainer.children.forEach {
            val child = it as MnemonicQuestionView
            if (!child.isVerified()) {
                return false
            }
        }
        return true
    }

    private fun isAllSelected(): Boolean {
        binding.mnemonicContainer.children.forEach {
            val child = it as MnemonicQuestionView
            if (!child.isChecked()) {
                return false
            }
        }
        return true
    }
}