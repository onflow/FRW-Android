package com.flowfoundation.wallet.page.wallet.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.flowfoundation.wallet.databinding.DialogMoveBinding
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.page.nft.move.SelectNFTDialog
import com.flowfoundation.wallet.page.token.detail.widget.MoveTokenDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class MoveDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogMoveBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogMoveBinding.inflate(inflater)
        return binding.rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            ivClose.setOnClickListener { dismissAllowingStateLoss() }
            clMoveNft.setOnClickListener {
                SelectNFTDialog.show(requireActivity())
                dismissAllowingStateLoss()
            }
            clMoveToken.setOnClickListener {
                MoveTokenDialog.show(requireActivity(), FlowCoin.SYMBOL_FLOW)
                dismissAllowingStateLoss()
            }
        }
    }

    companion object {
        fun show(fragmentManager: FragmentManager) {
            MoveDialog().show(fragmentManager, "")
        }
    }
}