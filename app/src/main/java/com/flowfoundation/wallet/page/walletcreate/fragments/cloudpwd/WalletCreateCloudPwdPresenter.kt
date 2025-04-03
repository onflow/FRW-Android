package com.flowfoundation.wallet.page.walletcreate.fragments.cloudpwd

import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentWalletCreateCloudPwdBinding
import com.flowfoundation.wallet.page.profile.subpage.backup.BackupGoogleDriveActivity
import com.flowfoundation.wallet.page.walletcreate.WALLET_CREATE_STEP_PIN_GUIDE
import com.flowfoundation.wallet.page.walletcreate.WalletCreateViewModel
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.listeners.SimpleTextWatcher
import com.flowfoundation.wallet.utils.setBackupGoogleDrive
import com.flowfoundation.wallet.utils.verifyPassword

class WalletCreateCloudPwdPresenter(
    private val fragment: Fragment,
    private val binding: FragmentWalletCreateCloudPwdBinding,
) : BasePresenter<WalletCreateCloudPwdModel> {

    private val viewModel by lazy { ViewModelProvider(fragment)[WalletCreateCloudPwdViewModel::class.java] }
    private val pageViewModel by lazy { ViewModelProvider(fragment.requireActivity())[WalletCreateViewModel::class.java] }
    private val rootView by lazy { fragment.requireActivity().findViewById<View>(R.id.rootView) }

    private val keyboardObserver by lazy { keyboardObserver() }

    init {
        with(binding) {
            pwdText1.setOnFocusChangeListener { _, _ -> onFocusChange() }
            pwdText2.setOnFocusChangeListener { _, _ -> onFocusChange() }

            pwdText1.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    updateButtonState()
                }
            })

            pwdText2.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    updateButtonState()
                }
            })
            checkBox.setOnCheckedChangeListener { _, _ -> updateButtonState() }

            nextButton.setOnClickListener { uploadMnemonic() }

            fragment.requireContext().getSystemService(AutofillManager::class.java)?.requestAutofill(pwdText1)
        }
        observeKeyboardVisible()
    }

    override fun bind(model: WalletCreateCloudPwdModel) {
        model.isBackupSuccess?.let { onBackupCallback(it) }
    }

    private fun onBackupCallback(isSuccess: Boolean) {
        if (isSuccess) {
            setBackupGoogleDrive(true)
            if (fragment.requireActivity() is BackupGoogleDriveActivity) {
                fragment.requireActivity().finish()
            } else {
                pageViewModel.changeStep(WALLET_CREATE_STEP_PIN_GUIDE)
            }
        } else {
            updateContentViewState(true)
            binding.nextButton.setProgressVisible(false)
            binding.nextButton.setText(R.string.next)
            Toast.makeText(fragment.requireContext(), R.string.backup_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateButtonState() {
        with(binding) {
            val pwd1 = pwdText1.text.toString()
            val pwd2 = pwdText2.text.toString()
            val isEnable = verifyPassword(pwd1) && pwd1 == pwd2 && checkBox.isChecked
            nextButton.isEnabled = isEnable
        }
    }

    private fun onFocusChange() {
        val focusedColor = ColorStateList.valueOf(R.color.text.res2color())
        val unfocusedColor = ColorStateList.valueOf(R.color.border_3.res2color())
        with(binding) {
            pwdText1.backgroundTintList = if (pwdText1.isFocused) focusedColor else unfocusedColor
            pwdText2.backgroundTintList = if (pwdText2.isFocused) focusedColor else unfocusedColor
        }
    }

    private fun uploadMnemonic() {
        with(binding) {
            nextButton.setProgressVisible(true)
            nextButton.setText(R.string.backing_up)
            viewModel.backup(fragment.requireContext(), pwdText1.text.toString())
            updateContentViewState(false)
        }
    }

    private fun updateContentViewState(isEnable: Boolean) {
        with(binding) {
            pwdText1.isEnabled = isEnable
            pwdText2.isEnabled = isEnable
            checkBox.isEnabled = isEnable
        }
    }

    private fun observeKeyboardVisible() {
        rootView.post { rootView.viewTreeObserver.addOnGlobalLayoutListener(keyboardObserver) }
    }

    private fun keyboardObserver(): ViewTreeObserver.OnGlobalLayoutListener {
        return ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val contentHeight = rootView.rootView.height

            val isKeyboardVisible = contentHeight - rect.bottom > contentHeight * 0.15f
            with(binding.placeholder.layoutParams as ConstraintLayout.LayoutParams) {
                dimensionRatio = if (isKeyboardVisible) "16:2" else "16:4.5"
                binding.placeholder.layoutParams = this
            }
            with(binding.contentWrapper.layoutParams as ViewGroup.MarginLayoutParams) {
                bottomMargin = if (isKeyboardVisible) contentHeight - rect.bottom else 0
                binding.contentWrapper.layoutParams = this
            }
        }
    }
}