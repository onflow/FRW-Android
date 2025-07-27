package com.flowfoundation.wallet.page.walletrestore.fragments.drivepwd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentWalletRestoreDrivePasswordBinding
import com.flowfoundation.wallet.manager.drive.DriveItem
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.walletrestore.requestWalletRestoreLogin
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.listeners.SimpleTextWatcher
import com.flowfoundation.wallet.utils.secret.aesDecrypt
import kotlinx.coroutines.delay

class WalletRestoreDrivePasswordFragment : Fragment() {

    private lateinit var binding: FragmentWalletRestoreDrivePasswordBinding

    private val data by lazy { arguments?.getParcelable<DriveItem>(EXTRA_DATA)!! }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWalletRestoreDrivePasswordBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pwdText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                updateTips()
            }
        })
        binding.nextButton.setOnClickListener { login() }
    }

    private fun updateTips(tip: String? = null) {
        with(binding) {
            nextButton.setProgressVisible(false)
            if (!tip.isNullOrBlank()) {
                stateText.text = tip
                stateText.setTextColor(R.color.error.res2color())
                stateIcon.setVisible(true)
            } else {
                nextButton.isEnabled = verifyPassword(pwdText.text.toString())
                stateIcon.setVisible(false)
                stateText.setTextColor(R.color.text_sub.res2color())
                stateText.setText(R.string.password_verify_format)
            }
        }
    }

    private fun login() {
        try {
            binding.nextButton.setProgressVisible(true)
            val mnemonic = aesDecrypt(binding.pwdText.text.toString(), message = data.data)
            ioScope {
                requestWalletRestoreLogin(mnemonic) { isSuccess, _ ->
                    uiScope {
                        if (!isSuccess) {
                            binding.nextButton.setProgressVisible(false)
                            Toast.makeText(requireContext(), "Network error", Toast.LENGTH_SHORT).show()
                        } else {
                            delay(200)
                            setBackupGoogleDrive(true)
                            MainActivity.relaunch(requireContext(), clearTop = true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            updateTips(getString(R.string.wrong_password))
            loge(e)
        }
    }

    companion object {
        private const val EXTRA_DATA = "EXTRA_DATA"

        fun instance(argument: DriveItem?): WalletRestoreDrivePasswordFragment {
            return WalletRestoreDrivePasswordFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_DATA, argument)
                }
            }
        }
    }
}