package com.flowfoundation.wallet.page.swap.dialog.confirm

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogSwapTokenConfirmBinding
import com.flowfoundation.wallet.page.swap.SwapViewModel
import com.flowfoundation.wallet.page.swap.fromAmount
import com.flowfoundation.wallet.page.swap.swapPageBinding
import com.flowfoundation.wallet.page.swap.toAmount
import com.flowfoundation.wallet.utils.format
import com.flowfoundation.wallet.utils.formatNum

class SwapTokenConfirmDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogSwapTokenConfirmBinding

    private val viewModel by lazy { ViewModelProvider(requireActivity())[SwapViewModel::class.java] }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSwapTokenConfirmBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.swapTransactionStateLiveData.observe(viewLifecycleOwner) {
            if (it) requireActivity().finish() else if (isResumed) {
                dismiss()
            }
        }
        binding.bindHeader()
        binding.bindEstimate()
        binding.sendButton.setOnProcessing { viewModel.swap() }
    }

    @SuppressLint("SetTextI18n")
    private fun DialogSwapTokenConfirmBinding.bindHeader() {
        val fromCoin = viewModel.fromCoin() ?: return
        val toCoin = viewModel.toCoin() ?: return
        Glide.with(fromAvatarView).load(fromCoin.icon()).into(fromAvatarView)
        Glide.with(toAvatarView).load(toCoin.icon()).into(toAvatarView)

        fromNameView.text = fromCoin.symbol.uppercase()
        toNameView.text = fromCoin.symbol.uppercase()

        val pageBinding = swapPageBinding() ?: return
        fromAddressView.text = "${pageBinding.fromAmount().format()} ${fromCoin.symbol.uppercase()}"
        toAddressView.text = "${pageBinding.toAmount().format()} ${toCoin.symbol.uppercase()}"
    }

    @SuppressLint("SetTextI18n")
    private fun DialogSwapTokenConfirmBinding.bindEstimate() {
        val data = viewModel.estimateLiveData.value ?: return
        val amountIn = data.routes.firstOrNull()?.routeAmountIn ?: return
        val amountOut = data.routes.firstOrNull()?.routeAmountOut ?: return
        val fromCoin = viewModel.fromCoin() ?: return
        val toCoin = viewModel.toCoin() ?: return

        bestPriceView.text = "1 ${fromCoin.symbol.uppercase()} ≈ ${(amountOut / amountIn).format()} ${toCoin.symbol.uppercase()}"

        providerIconView.setImageResource(R.drawable.ic_increment_fi)
        providerView.text = "Increment.fi"

        priceImpactView.text = data.priceImpact.formatNum(4)

        estimatedFeesView.text = data.priceImpact.formatNum(4)
    }


    companion object {
        fun show(fragmentManager: FragmentManager) {
            SwapTokenConfirmDialog().apply {
            }.show(fragmentManager, "")
        }
    }
}