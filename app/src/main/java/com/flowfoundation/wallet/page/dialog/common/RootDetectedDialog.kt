package com.flowfoundation.wallet.page.dialog.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.databinding.DialogRootDetectedBinding
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.isRootDetectedDialogShown
import com.flowfoundation.wallet.utils.isRooted
import com.flowfoundation.wallet.utils.setRootDetectedDialogShown
import com.flowfoundation.wallet.utils.uiScope

class RootDetectedDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogRootDetectedBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogRootDetectedBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ioScope { setRootDetectedDialogShown() }
        binding.root.requestFocus()

        binding.closeButton.setOnClickListener { dismiss() }
        binding.startButton.setOnClickListener {
            dismiss()
        }
    }

    companion object {

        fun show(fragmentManager: FragmentManager) {
            ioScope {
                if (WalletManager.wallet() == null || isRootDetectedDialogShown() || !isRooted()) {
                    return@ioScope
                }
                uiScope { RootDetectedDialog().showNow(fragmentManager, "") }
            }
        }
    }
}