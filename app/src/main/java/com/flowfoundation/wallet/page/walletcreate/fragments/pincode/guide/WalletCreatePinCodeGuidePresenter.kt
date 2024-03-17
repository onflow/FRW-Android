package com.flowfoundation.wallet.page.walletcreate.fragments.pincode.guide

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentWalletCreatePinGuideBinding
import com.flowfoundation.wallet.manager.biometric.BlockBiometricManager
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.walletcreate.WALLET_CREATE_STEP_PIN_CODE
import com.flowfoundation.wallet.page.walletcreate.WalletCreateViewModel
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.setBiometricEnable
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.toast

class WalletCreatePinCodeGuidePresenter(
    private val fragment: Fragment,
    private val binding: FragmentWalletCreatePinGuideBinding,
) {
    private val pageViewModel by lazy { ViewModelProvider(fragment.requireActivity())[WalletCreateViewModel::class.java] }

    init {
        setRegistered()
        with(binding) {
            title1.text = SpannableString(R.string.pin_code_title.res2String()).apply {
                val protection = R.string.protection.res2String()
                val index = indexOf(protection)
                setSpan(ForegroundColorSpan(R.color.colorSecondary.res2color()), index, index + protection.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            faceIdLayout.setOnClickListener {
                BlockBiometricManager.showBiometricPrompt(fragment.requireActivity()) { isSuccess ->
                    if (isSuccess) {
                        setBiometricEnable(true)
                        if (fragment.isAdded) {
                            MainActivity.launch(fragment.requireContext())
                        }
                    } else {
                        toast(msgRes = R.string.auth_error)
                    }
                }
            }
            pinCodeLayout.setOnClickListener { pageViewModel.changeStep(WALLET_CREATE_STEP_PIN_CODE) }

            faceIdLayout.setVisible(BlockBiometricManager.checkIsBiometricEnable(fragment.requireContext()))
        }
    }
}