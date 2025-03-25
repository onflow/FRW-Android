package com.flowfoundation.wallet.page.walletcreate.fragments.warning

import android.content.res.ColorStateList
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentWalletCreateWarningBinding
import com.flowfoundation.wallet.page.walletcreate.WalletCreateViewModel
import com.flowfoundation.wallet.utils.extensions.res2color

class WalletCreateWarningPresenter(
    private val fragment: Fragment,
    private val binding: FragmentWalletCreateWarningBinding,
) : BasePresenter<WalletCreateWarningModel> {

    private val pageViewModel by lazy { ViewModelProvider(fragment.requireActivity())[WalletCreateViewModel::class.java] }

    private var isRequesting = false

    init {
        with(binding) {
            warningCheck1.setOnCheckedChangeListener { _, _ -> onCheckChanged() }
            warningCheck2.setOnCheckedChangeListener { _, _ -> onCheckChanged() }
            warningCheck3.setOnCheckedChangeListener { _, _ -> onCheckChanged() }
            nextButton.setOnClickListener {
                pageViewModel.nextStep()
            }
        }
    }

    override fun bind(model: WalletCreateWarningModel) {
        model.isRegisterSuccess?.let { registerCallback(it) }
    }

    private fun registerCallback(isRegisterSuccess: Boolean) {
        isRequesting = false
        if (isRegisterSuccess) {
            pageViewModel.nextStep()
        } else {
            updateButtonState()
            Toast.makeText(fragment.requireContext(), R.string.register_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateButtonState() {
        with(binding.nextButton) {
            setProgressVisible(isRequesting)
            if (isRequesting) {
                setText(R.string.almost_there)
            } else {
                setText(R.string.confirm)
            }
        }
    }


    private fun onCheckChanged() {
        val uncheckedColor = R.color.border_3.res2color()
        val checkedColor = R.color.colorSecondary.res2color()
        with(binding) {
            val isConfirmed = warningCheck1.isChecked && warningCheck2.isChecked && warningCheck3.isChecked
            nextButton.isEnabled = isConfirmed
            warning1.backgroundTintList = ColorStateList.valueOf(if (warningCheck1.isChecked) checkedColor else uncheckedColor)
            warning2.backgroundTintList = ColorStateList.valueOf(if (warningCheck2.isChecked) checkedColor else uncheckedColor)
            warning3.backgroundTintList = ColorStateList.valueOf(if (warningCheck3.isChecked) checkedColor else uncheckedColor)
        }
    }
}