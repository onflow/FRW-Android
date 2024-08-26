package com.flowfoundation.wallet.page.restore.keystore.fragment

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogKeystoreSelectAccountBinding
import com.flowfoundation.wallet.page.restore.keystore.adapter.KeyStoreSelectAccountAdapter
import com.flowfoundation.wallet.page.restore.keystore.model.KeystoreAddress
import com.flowfoundation.wallet.page.restore.keystore.viewmodel.KeyStoreRestoreViewModel
import com.flowfoundation.wallet.utils.toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class KeyStoreSelectAccountDialog: BottomSheetDialogFragment() {

    private lateinit var binding: DialogKeystoreSelectAccountBinding
    private var result: Continuation<KeystoreAddress?>? = null

    private val viewModel by lazy {
        ViewModelProvider(requireActivity())[KeyStoreRestoreViewModel::class.java]
    }
    private val adapter by lazy {
        KeyStoreSelectAccountAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogKeystoreSelectAccountBinding.inflate(inflater)
        return binding.root
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        result?.resume(null)
    }

    override fun onResume() {
        if (result == null) {
            dismiss()
        }
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = viewModel.getAddressList()
        with(binding) {
            titleView.text = view.context.getString(R.string.keystore_select_account_title, list.size)
            ivClose.setOnClickListener {
                dismiss()
            }
            btnImport.setOnClickListener {
                val address = adapter.getSelectedKeystoreAddress()
                if (address == null) {
                    toast(msg = "Please select an account")
                } else {
                    result?.resume(address)
                }
                dismiss()
            }
        }
        with(binding.rvAccountList) {
            adapter = this@KeyStoreSelectAccountDialog.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        }
        adapter.setNewDiffData(list)
    }

    suspend fun show(
        fragmentManager: FragmentManager
    ) = suspendCoroutine { result ->
        this.result = result
        show(fragmentManager, "")
    }
}