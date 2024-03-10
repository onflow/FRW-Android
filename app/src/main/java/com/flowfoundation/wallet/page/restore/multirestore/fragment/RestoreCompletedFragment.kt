package com.flowfoundation.wallet.page.restore.multirestore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentRestoreCompletedBinding
import com.flowfoundation.wallet.page.restore.multirestore.viewmodel.MultiRestoreViewModel


class RestoreCompletedFragment: Fragment() {
    private lateinit var binding: FragmentRestoreCompletedBinding
    private val restoreViewModel by lazy {
        ViewModelProvider(requireActivity())[MultiRestoreViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestoreCompletedBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            val optionList = restoreViewModel.getRestoreOptionList()
            ivOptionIconFirst.setImageResource(optionList[0].iconId)
            ivOptionIconSecond.setImageResource(optionList[1].iconId)
            btnNext.setOnClickListener {
                if (btnNext.isProgressVisible()) {
                    return@setOnClickListener
                }
                btnNext.setProgressVisible(true)
                restoreViewModel.restoreWallet()
            }
        }
    }
}