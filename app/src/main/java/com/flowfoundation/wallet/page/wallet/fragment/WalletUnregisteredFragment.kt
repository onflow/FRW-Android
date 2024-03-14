package com.flowfoundation.wallet.page.wallet.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.databinding.FragmentWalletUnregisteredBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.page.wallet.adapter.WalletAccountAdapter
import com.flowfoundation.wallet.page.wallet.sync.WalletSyncActivity
import com.flowfoundation.wallet.page.walletcreate.WalletCreateActivity
import com.flowfoundation.wallet.page.restore.WalletRestoreActivity
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.visible

class WalletUnregisteredFragment : Fragment() {

    private lateinit var binding: FragmentWalletUnregisteredBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWalletUnregisteredBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            val layoutParams = ifvLogo.layoutParams as ConstraintLayout.LayoutParams
            if (AccountManager.list().isNotEmpty()) {
                clAccountLayout.visible()
                ifvLogo.layoutParams = layoutParams.apply {
                    width = 92.dp2px().toInt()
                    height = 92.dp2px().toInt()
                }
                rvAccountList.layoutManager = LinearLayoutManager(context)
                rvAccountList.adapter = WalletAccountAdapter().apply {
                    setNewDiffData(AccountManager.list())
                }
            } else {
                clAccountLayout.gone()
                ifvLogo.layoutParams = layoutParams.apply {
                    width = 130.dp2px().toInt()
                    height = 130.dp2px().toInt()
                }
            }
            createButton.setOnClickListener { WalletCreateActivity.launch(requireContext()) }
            importButton.setOnClickListener { WalletRestoreActivity.launch(requireContext()) }
        }
    }
}