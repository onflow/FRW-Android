package com.flowfoundation.wallet.page.walletcreate.presenter

import androidx.transition.Fade
import androidx.transition.Transition
import com.google.android.material.transition.MaterialSharedAxis
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.page.walletcreate.*
import com.flowfoundation.wallet.page.walletcreate.fragments.cloudpwd.WalletCreateCloudPwdFragment
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.WalletCreateMnemonicFragment
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemoniccheck.WalletCreateMnemonicCheckFragment
import com.flowfoundation.wallet.page.walletcreate.fragments.pincode.guide.WalletCreatePinCodeGuideFragment
import com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.WalletCreatePinCodeFragment
import com.flowfoundation.wallet.page.walletcreate.fragments.username.WalletCreateUsernameFragment
import com.flowfoundation.wallet.page.walletcreate.fragments.warning.WalletCreateWarningFragment
import com.flowfoundation.wallet.page.walletcreate.model.WalletCreateContentModel


class WalletCreateContentPresenter(
    private val activity: WalletCreateActivity,
) : BasePresenter<WalletCreateContentModel> {

    private var currentStep = -1

    override fun bind(model: WalletCreateContentModel) {
        model.changeStep?.let { onStepChane(it) }
    }

    private fun onStepChane(step: Int) {
        val transition = createTransition(currentStep, step)
        val fragment = when (step) {
            WALLET_CREATE_STEP_WARNING -> WalletCreateWarningFragment()
            WALLET_CREATE_STEP_MNEMONIC -> WalletCreateMnemonicFragment()
            WALLET_CREATE_STEP_CLOUD_PWD -> WalletCreateCloudPwdFragment()
            WALLET_CREATE_STEP_MNEMONIC_CHECK -> WalletCreateMnemonicCheckFragment()
            WALLET_CREATE_STEP_PIN_GUIDE -> WalletCreatePinCodeGuideFragment()
            WALLET_CREATE_STEP_PIN_CODE -> WalletCreatePinCodeFragment()
            else -> WalletCreateUsernameFragment()
        }
        fragment.enterTransition = transition
        activity.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).commit()
        currentStep = step
    }

    private fun createTransition(currentStep: Int, newStep: Int): Transition {
        if (currentStep < 0) {
            return Fade().apply { duration = 50 }
        }
        val transition = MaterialSharedAxis(MaterialSharedAxis.X, currentStep < newStep)

        transition.addTarget(getRootIdByStep(currentStep))
        transition.addTarget(getRootIdByStep(newStep))
        return transition
    }
}