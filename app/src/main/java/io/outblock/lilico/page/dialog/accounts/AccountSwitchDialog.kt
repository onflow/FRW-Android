package io.outblock.lilico.page.dialog.accounts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.outblock.lilico.databinding.DialogAccountSwitchBinding
import io.outblock.lilico.manager.account.AccountManager
import io.outblock.lilico.page.dialog.accounts.adapter.AccountListAdapter
import io.outblock.lilico.page.restore.WalletRestoreActivity
import io.outblock.lilico.page.walletcreate.WALLET_CREATE_STEP_USERNAME
import io.outblock.lilico.page.walletcreate.WalletCreateActivity

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