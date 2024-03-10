package io.outblock.lilico.page.dialog.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.outblock.lilico.databinding.DialogRootDetectedBinding
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.utils.Env
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.isRootDetectedDialogShown
import io.outblock.lilico.utils.isRooted
import io.outblock.lilico.utils.setRootDetectedDialogShown
import io.outblock.lilico.utils.uiScope

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