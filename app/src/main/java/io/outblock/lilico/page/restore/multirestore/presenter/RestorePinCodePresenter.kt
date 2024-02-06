package io.outblock.lilico.page.restore.multirestore.presenter

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.outblock.lilico.R
import io.outblock.lilico.databinding.FragmentRestorePinCodeBinding
import io.outblock.lilico.page.restore.multirestore.viewmodel.MultiRestoreViewModel
import io.outblock.lilico.page.restore.multirestore.viewmodel.RestoreGoogleDriveWithPinViewModel
import io.outblock.lilico.page.walletcreate.fragments.pincode.pin.KeyboardItem
import io.outblock.lilico.page.walletcreate.fragments.pincode.pin.widgets.KeyboardType
import io.outblock.lilico.page.walletcreate.fragments.pincode.pin.widgets.keyboardT9Normal
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.extensions.res2color


class RestorePinCodePresenter(
    private val fragment: Fragment,
    binding: FragmentRestorePinCodeBinding
) {

    private val withPinViewModel by lazy {
        ViewModelProvider(fragment.requireParentFragment())[RestoreGoogleDriveWithPinViewModel::class.java]
    }

    private val restoreViewModel by lazy {
        ViewModelProvider(fragment.requireParentFragment().requireActivity())[MultiRestoreViewModel::class.java]
    }

    init {
        with(binding) {
            pinTitle.text = SpannableString(
                R.string.verify_pin.res2String()
            ).apply {
                val protection = R.string.pin.res2String()
                val index = indexOf(protection)
                setSpan(
                    ForegroundColorSpan(R.color.accent_green.res2color()),
                    index,
                    index + protection.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            pinTip.text = SpannableString(
                R.string.enter_verify_pin_tip.res2String()
            ).apply {
                val protection = R.string.pin.res2String()
                val index = indexOf(protection)
                setSpan(
                    ForegroundColorSpan(R.color.accent_green.res2color()),
                    index,
                    index + protection.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
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
                restoreViewModel.setPinCode(pinCode)
                withPinViewModel.restoreGoogleDrive()
            }
        }
    }
}