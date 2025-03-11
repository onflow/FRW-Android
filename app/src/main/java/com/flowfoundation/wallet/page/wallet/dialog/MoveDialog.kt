package com.flowfoundation.wallet.page.wallet.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogMoveBinding
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.page.nft.move.SelectNFTDialog
import com.flowfoundation.wallet.page.token.detail.widget.MoveTokenDialog
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.invisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.setDoNotShowMoveDialog
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class MoveDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogMoveBinding
    private var result: Continuation<Boolean>? = null
    private var appName: String? = null

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
            ivClose.setOnClickListener {
                result?.resume(true)
                dismissAllowingStateLoss()
            }
            clMoveNft.setOnClickListener {
                uiScope {
                    SelectNFTDialog().show(requireActivity()).let {
                        result?.resume(true)
                    }
                    dismissAllowingStateLoss()
                }
            }
            clMoveToken.setOnClickListener {
                if (EVMWalletManager.haveEVMAddress()) {
                    uiScope {
                        MoveTokenDialog().showDialog(requireActivity(), FlowCoinListManager.getFlowCoinContractId()).let {
                            result?.resume(true)
                        }
                        dismissAllowingStateLoss()
                    }
                } else {
                    toast(msgRes = R.string.features_coming)
                }
            }

            if (appName.isNullOrBlank()) {
                tvDesc.setText(R.string.move_to_desc)
                btnSkip.invisible()
                llCheck.gone()
            } else {
                tvDesc.text = getString(R.string.move_to_desc_borwser, appName)
                btnSkip.visible()
                btnSkip.setOnClickListener {
                    result?.resume(true)
                    dismissAllowingStateLoss()
                }
                llCheck.visible()
                llCheck.setOnClickListener {
                    checkBox.isChecked = checkBox.isChecked.not()
                    ioScope {
                        setDoNotShowMoveDialog(checkBox.isChecked)
//                        BackupTipManager.markDoNotShow(checkBox.isChecked)

                    }
                }
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        result?.resume(true)
    }

    suspend fun showMove(
        fragmentManager: FragmentManager,
        name: String? = null
    ) = suspendCoroutine { result->
        this.result = result
        this.appName = name
        show(fragmentManager, "")
    }
}