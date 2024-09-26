package com.flowfoundation.wallet.page.restore.keystore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.flowfoundation.wallet.databinding.DialogKeystoreNoAccountBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class KeyStoreNoAccountDialog: BottomSheetDialogFragment() {
    private lateinit var binding: DialogKeystoreNoAccountBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogKeystoreNoAccountBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            btnOk.setOnClickListener { dismiss() }
            ivClose.setOnClickListener { dismiss() }
        }
    }
}