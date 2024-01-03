package io.outblock.lilico.page.restore.multirestore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.outblock.lilico.databinding.FragmentRestoreStartBinding
import io.outblock.lilico.page.restore.multirestore.model.RestoreOption
import io.outblock.lilico.page.restore.multirestore.viewmodel.MultiRestoreViewModel
import io.outblock.lilico.utils.extensions.setVisible


class RestoreStartFragment: Fragment() {
    private lateinit var binding: FragmentRestoreStartBinding

    private val restoreViewModel by lazy {
        ViewModelProvider(requireActivity())[MultiRestoreViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestoreStartBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            btnNext.setVisible(false)
            oiGoogleDrive.setOnClickListener {
                restoreViewModel.selectOption(RestoreOption.RESTORE_FROM_GOOGLE_DRIVE) { isSelected ->
                    oiGoogleDrive.changeItemStatus(isSelected)
                }
                checkRestoreValid()
            }
            oiRecoveryPhrase.setOnClickListener {
                restoreViewModel.selectOption(RestoreOption.RESTORE_FROM_RECOVERY_PHRASE) { isSelected ->
                    oiRecoveryPhrase.changeItemStatus(isSelected)
                }
                checkRestoreValid()
            }
            btnNext.setOnClickListener {
                restoreViewModel.startRestore()
            }
        }
    }

    private fun checkRestoreValid() {
        binding.btnNext.setVisible(restoreViewModel.isRestoreValid())
    }
}