package com.flowfoundation.wallet.page.wallet.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.Html
import android.text.method.LinkMovementMethod
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentWalletUnregisteredBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.page.wallet.adapter.WalletAccountAdapter
import com.flowfoundation.wallet.page.walletcreate.WalletCreateActivity
import com.flowfoundation.wallet.page.restore.WalletRestoreActivity
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog

class WalletUnregisteredFragment : Fragment() {

    private lateinit var binding: FragmentWalletUnregisteredBinding
    private val adapter by lazy { WalletAccountAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWalletUnregisteredBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        //val layoutParams = binding.ifvLogo.layoutParams as ConstraintLayout.LayoutParams
        with(binding) {
            clAccountLayout.gone()
//            ifvLogo.layoutParams = layoutParams.apply {
//                width = 130.dp2px().toInt()
//                height = 130.dp2px().toInt()
//            }
            createButton.setOnClickListener {
                if (isTestnet()) {
                    SwitchNetworkDialog(requireContext(), DialogType.CREATE).show()
                } else {
                    WalletCreateActivity.launch(requireContext())
                }
            }
            importButton.setOnClickListener { WalletRestoreActivity.launch(requireContext()) }
        }

        binding.legalText.text = Html.fromHtml(getString(R.string.legal_message), Html.FROM_HTML_MODE_LEGACY)
        binding.legalText.movementMethod = LinkMovementMethod.getInstance()

        ioScope {
            val list = AccountManager.getSwitchAccountList()
            if (list.isNotEmpty()) {
                uiScope {
                    with(binding) {
                        clAccountLayout.visible()
                        descView.gone()
                        onFlow.gone()
//                        ifvLogo.layoutParams = layoutParams.apply {
//                            width = 92.dp2px().toInt()
//                            height = 92.dp2px().toInt()
//                        }
                        rvAccountList.layoutManager = LinearLayoutManager(context)
                        rvAccountList.adapter = adapter
                    }
                    adapter.setNewDiffData(list)
                }
            }
        }
    }
}