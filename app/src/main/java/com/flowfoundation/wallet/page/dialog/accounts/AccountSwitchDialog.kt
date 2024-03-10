package com.flowfoundation.wallet.page.dialog.accounts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.databinding.DialogAccountSwitchBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.page.dialog.accounts.adapter.AccountListAdapter
import com.flowfoundation.wallet.page.restore.WalletRestoreActivity
import com.flowfoundation.wallet.page.walletcreate.WALLET_CREATE_STEP_USERNAME
import com.flowfoundation.wallet.page.walletcreate.WalletCreateActivity

class AccountSwitchDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogAccountSwitchBinding

    private val adapter by lazy { AccountListAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogAccountSwitchBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.requestFocus()

        binding.tvImportAccount.setOnClickListener {
            WalletRestoreActivity.launch(requireContext())
            dismiss()
        }
        binding.tvNewAccount.setOnClickListener {
            WalletCreateActivity.launch(requireContext(), step = WALLET_CREATE_STEP_USERNAME)
            dismiss()
        }

        with(binding.recyclerView) {
            adapter = this@AccountSwitchDialog.adapter
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        }

        adapter.setNewDiffData(AccountManager.list())
    }

    companion object {

        fun show(fragmentManager: FragmentManager) {
            AccountSwitchDialog().showNow(fragmentManager, "")
        }
    }
}