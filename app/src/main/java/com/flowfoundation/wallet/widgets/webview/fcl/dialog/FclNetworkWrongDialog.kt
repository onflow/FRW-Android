package com.flowfoundation.wallet.widgets.webview.fcl.dialog

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogFclWrongNetworkBinding
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.app.doNetworkChangeTask
import com.flowfoundation.wallet.manager.app.isMainnet
import com.flowfoundation.wallet.manager.app.networkId
import com.flowfoundation.wallet.manager.app.refreshChainNetworkSync
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.flowjvm.FlowApi
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.utils.extensions.capitalizeV2
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.updateChainNetworkPreference
import com.flowfoundation.wallet.widgets.FlowLoadingDialog
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel
import kotlinx.coroutines.delay

class FclNetworkWrongDialog : BottomSheetDialogFragment() {

    private val data by lazy { arguments?.getParcelable<FclDialogModel>(EXTRA_DATA) }

    private lateinit var binding: DialogFclWrongNetworkBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogFclWrongNetworkBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        data ?: return
        val data = data ?: return
        with(binding) {
            val descSb = SpannableStringBuilder(getString(R.string.network_error_dialog_desc,
                chainNetWorkString(), data.network))
            val index1 = descSb.indexOf(chainNetWorkString())
            descSb.setSpan(StyleSpan(Typeface.BOLD), index1, index1 + (chainNetWorkString().length), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val index2 = descSb.indexOf(data.network.orEmpty())
            descSb.setSpan(StyleSpan(Typeface.BOLD), index2, index2 + (data.network?.length ?: 0), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            descView.text = descSb

            fromNetworkNameView.text = chainNetWorkString().capitalizeV2()
            toNetworkNameView.text = data.network?.capitalizeV2()
            fromNetworkIcon.setImageResource(if (isMainnet()) R.drawable.ic_network_mainnet else R.drawable.ic_network_testnet)
            toNetworkIcon.setImageResource(if (isMainnet()) R.drawable.ic_network_testnet else R.drawable.ic_network_mainnet)

            cancelButton.setOnClickListener {
                dismiss()
            }
            approveButton.setOnClickListener {
                changeNetwork(networkId(data.network!!.lowercase()))
            }
        }
    }

    private fun changeNetwork(network: Int) {
        updateChainNetworkPreference(network) {
            ioScope {
                delay(200)
                refreshChainNetworkSync()
                doNetworkChangeTask()
                FlowApi.refreshConfig()
                FlowCadenceApi.refreshConfig()
                uiScope {
                    FlowLoadingDialog(requireContext()).show()
                    WalletManager.changeNetwork()
                    clearUserCache()
                    MainActivity.relaunch(requireContext(), true)
                    dismiss()
                }
            }
        }
    }

    companion object {
        private const val EXTRA_DATA = "extra_data"

        fun show(fragmentManager: FragmentManager, data: FclDialogModel) {
            FclNetworkWrongDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_DATA, data)
                }
            }.show(fragmentManager, "")
        }
    }
}


fun checkAndShowNetworkWrongDialog(
    fragmentManager: FragmentManager,
    data: FclDialogModel,
): Boolean {
    return if (data.network != null && data.network.lowercase() != chainNetWorkString()) {
        FclNetworkWrongDialog.show(fragmentManager, data)
        true
    } else false
}