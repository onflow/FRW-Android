package com.flowfoundation.wallet.page.restore.multirestore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentRestoreErrorBinding
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreErrorOption
import com.flowfoundation.wallet.page.restore.multirestore.viewmodel.MultiRestoreViewModel
import com.flowfoundation.wallet.utils.extensions.res2String


class RestoreDropboxErrorFragment(val option: RestoreErrorOption) : Fragment() {

    private lateinit var binding: FragmentRestoreErrorBinding

    private val restoreViewModel by lazy {
        ViewModelProvider(requireParentFragment().requireActivity())[MultiRestoreViewModel::class.java]
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
                    RestoreErrorOption.BACKUP_NOT_FOUND -> {
                        restoreViewModel.toDropbox()
                    }
                    RestoreErrorOption.BACKUP_DECRYPTION_FAILED -> {
                        restoreViewModel.toDropboxPinCode(restoreViewModel.getMnemonicData())
                    }
                    else -> {
                        restoreViewModel.toDropbox()
                    }
                }
            }
        }
    }
}