package com.flowfoundation.wallet.page.wallet.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.flowfoundation.wallet.databinding.DialogSwapProviderBinding
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.page.browser.openBrowser
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class SwapProviderDialog : BottomSheetDialogFragment() {

    private val isFlowToken by lazy { arguments?.getBoolean(EXTRA_IS_FLOW_TOKEN) ?: false }
    private val isEVMToken by lazy { arguments?.getBoolean(EXTRA_IS_EVM_TOKEN) ?: false }
    private lateinit var binding: DialogSwapProviderBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogSwapProviderBinding.inflate(inflater)
        return binding.rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            cvIncrement.visibility = if (isEVMToken) View.GONE else View.VISIBLE
            cvTrado.visibility = if (isFlowToken || isEVMToken) View.VISIBLE else View.GONE
            cvKittyPunch.visibility = if (isFlowToken || isEVMToken) View.VISIBLE else View.GONE
            cvIncrement.setOnClickListener {
                openBrowser(
                    requireActivity(),
                    "https://${if (isTestnet()) "demo" else "app"}" +
                            ".increment.fi/swap"
                )
                dismissAllowingStateLoss()
            }
            cvTrado.setOnClickListener {
                openBrowser(
                    requireActivity(),
                    "https://spot.trado.one/trade/swap"
                )
                dismissAllowingStateLoss()
            }
            cvKittyPunch.setOnClickListener {
                openBrowser(
                    requireActivity(),
                    "https://swap.kittypunch.xyz/"
                )
                dismissAllowingStateLoss()
            }
            closeButton.setOnClickListener { dismissAllowingStateLoss() }
        }
    }

    companion object {
        private const val EXTRA_IS_FLOW_TOKEN = "extra_is_flow_token"
        private const val EXTRA_IS_EVM_TOKEN = "extra_is_evm_token"
        fun show(
            fragmentManager: FragmentManager,
            isFlowToken: Boolean = false,
            isEVMToken: Boolean = false
        ) {
            SwapProviderDialog().apply {
                arguments = Bundle().apply {
                    putBoolean(EXTRA_IS_FLOW_TOKEN, isFlowToken)
                    putBoolean(EXTRA_IS_EVM_TOKEN, isEVMToken)
                }
            }.show(fragmentManager, "")
        }
    }
}