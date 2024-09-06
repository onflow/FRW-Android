package com.flowfoundation.wallet.page.backup.presenter

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentRestorePinCodeBinding
import com.flowfoundation.wallet.manager.backup.decryptMnemonic
import com.flowfoundation.wallet.page.backup.viewmodel.BackupViewMnemonicViewModel
import com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.KeyboardItem
import com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.widgets.KeyboardType
import com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.widgets.keyboardT9Normal
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setSpannableText
import wallet.core.jni.HDWallet


class ViewPinCodePresenter(
    private val fragment: Fragment,
    private val binding: FragmentRestorePinCodeBinding
) {

    private val viewModel by lazy {
        ViewModelProvider(
            fragment.requireActivity()
        )[BackupViewMnemonicViewModel::class.java]
    }

    init {
        with(binding) {
            pinTitle.setSpannableText(
                R.string.verify_pin.res2String(),
                R.string.pin.res2String(),
                R.color.accent_green.res2color()
            )
            pinTip.setSpannableText(
                R.string.enter_verify_pin_tip.res2String(),
                R.string.pin.res2String(),
                R.color.accent_green.res2color()
            )
            pinKeyboard.setOnKeyboardActionListener { onKeyPressed(it) }
            pinKeyboard.bindKeys(keyboardT9Normal, KeyboardType.T9)
            pinKeyboard.show()
        }
    }

    private fun FragmentRestorePinCodeBinding.onKeyPressed(key: KeyboardItem) {
        if (key.type == KeyboardItem.TYPE_DELETE_KEY || key.type == KeyboardItem.TYPE_CLEAR_KEY) {
            pinInput.delete()
        } else {
            pinInput.input(key)
            if (pinInput.keys().size == 6) {
                val pinCode = pinInput.keys().joinToString("") { "${it.number}" }
                checkPinCode(pinCode)
            }
        }
    }

    private fun checkPinCode(pinCode: String) {
        try {
            val data = viewModel.getMnemonicData()
            if (data.isEmpty()) {
                viewModel.toBackupNotFound()
                return
            }
            val mnemonic = decryptMnemonic(data, pinCode)
            // check mnemonic
            HDWallet(mnemonic, "")
            viewModel.showRecoveryPhrase(mnemonic)
        } catch (e: Exception) {
            binding.pinInput.shakeAndClear(keysCLear = true)
            viewModel.toBackupDecryptionFailed()
        }
    }
}