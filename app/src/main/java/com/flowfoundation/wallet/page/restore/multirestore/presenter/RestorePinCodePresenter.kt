package com.flowfoundation.wallet.page.restore.multirestore.presenter

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentRestorePinCodeBinding
import com.flowfoundation.wallet.manager.backup.decryptMnemonic
import com.flowfoundation.wallet.page.restore.multirestore.viewmodel.MultiRestoreViewModel
import com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.KeyboardItem
import com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.widgets.KeyboardType
import com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.widgets.keyboardT9Normal
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setSpannableText
import wallet.core.jni.HDWallet


class RestorePinCodePresenter(
    private val fragment: Fragment,
    private val binding: FragmentRestorePinCodeBinding
) {

    private val restoreViewModel by lazy {
        ViewModelProvider(fragment.requireParentFragment().requireActivity())[MultiRestoreViewModel::class.java]
    }

    init {
        with(binding) {
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
            val data = restoreViewModel.getMnemonicData()
            if (data.isEmpty()) {
                restoreViewModel.toBackupNotFound()
                return
            }
            val mnemonic = decryptMnemonic(data, pinCode)
            // check mnemonic
            HDWallet(mnemonic, "")
            restoreViewModel.addMnemonicToTransaction(mnemonic)
        } catch (e: Exception) {
            binding.pinInput.shakeAndClear(keysCLear = true)
            restoreViewModel.toBackupDecryptionFailed()
        }
    }
}