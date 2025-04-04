package com.flowfoundation.wallet.page.walletcreate.fragments.pincode.guide

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentWalletCreatePinGuideBinding
import com.flowfoundation.wallet.manager.biometric.BlockBiometricManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.MixpanelSecurityTool
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.walletcreate.WALLET_CREATE_STEP_PIN_CODE
import com.flowfoundation.wallet.page.walletcreate.WalletCreateViewModel
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.setBiometricEnable
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.toast

class WalletCreatePinCodeGuidePresenter(
    private val fragment: Fragment,
    binding: FragmentWalletCreatePinGuideBinding,
) {
    private val pageViewModel by lazy { ViewModelProvider(fragment.requireActivity())[WalletCreateViewModel::class.java] }

    init {
        setRegistered()
        with(binding) {
            faceIdLayout.setOnClickListener {
                BlockBiometricManager.showBiometricPrompt(fragment.requireActivity()) { isSuccess, errorMsg ->
                    if (isSuccess) {
                        setBiometricEnable(true)
                        MixpanelManager.securityTool(MixpanelSecurityTool.BIOMETRIC)
                        if (fragment.isAdded) {
                            MainActivity.launch(fragment.requireContext())
                        }
                    } else {
                        toast(msg = errorMsg)
                    }
                }
            }
            pinCodeLayout.setOnClickListener { pageViewModel.changeStep(WALLET_CREATE_STEP_PIN_CODE) }

            faceIdLayout.setVisible(BlockBiometricManager.checkIsBiometricEnable(fragment.requireContext()))
        }
    }
}