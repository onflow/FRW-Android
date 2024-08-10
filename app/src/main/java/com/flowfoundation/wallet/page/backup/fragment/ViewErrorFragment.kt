package com.flowfoundation.wallet.page.backup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentRestoreErrorBinding
import com.flowfoundation.wallet.page.backup.viewmodel.BackupViewMnemonicViewModel
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreErrorOption
import com.flowfoundation.wallet.utils.extensions.res2String


class ViewErrorFragment(val option: RestoreErrorOption) : Fragment() {

    private lateinit var binding: FragmentRestoreErrorBinding

    private val restoreViewModel by lazy {
        ViewModelProvider(requireActivity())[BackupViewMnemonicViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestoreErrorBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            tvErrorTitle.text = option.titleId.res2String()
            tvErrorDesc.text = option.descId.res2String()
            tvTry.setOnClickListener {
                when (option) {
                    RestoreErrorOption.BACKUP_DECRYPTION_FAILED -> {
                        restoreViewModel.toPinCode(restoreViewModel.getMnemonicData())
                    }
                    else -> {
                        requireActivity().finish()
                    }
                }
            }
        }
    }
}