package com.flowfoundation.wallet.page.token.custom.widget

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogAddCustomTokenBinding
import com.flowfoundation.wallet.manager.coin.CustomTokenManager
import com.flowfoundation.wallet.page.token.custom.WatchAssetsViewModel
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class AddCustomTokenDialog : BottomSheetDialogFragment() {

    private val contractAddress by lazy { arguments?.getString(EXTRA_CONTRACT_ADDRESS, "") ?: "" }
    private val tokenIcon by lazy { arguments?.getString(EXTRA_TOKEN_ICON) }
    private lateinit var binding: DialogAddCustomTokenBinding
    private lateinit var viewModel: WatchAssetsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogAddCustomTokenBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (contractAddress.isEmpty()) {
            dismiss()
            return
        }
        viewModel = ViewModelProvider(this)[WatchAssetsViewModel::class.java].apply {
            tokenInfoLiveData.observe(this@AddCustomTokenDialog) { token ->
                with(binding) {
                    lavLoading.gone()
                    clTokenLayout.visible()
                    Glide.with(ivTokenIcon).load(token.icon()).into(ivTokenIcon)
                    tvTokenName.text = token.name
                    btnAdd.isEnabled = token.isEnable()
                    btnAdd.setOnClickListener {
                        CustomTokenManager.addEVMCustomToken(token)
                        toast(msgRes = R.string.add_token_success)
                        approveCallback?.invoke(true)
                        dismiss()
                    }
                }
            }
            balanceWithSymbolLiveData.observe(this@AddCustomTokenDialog) {
                binding.tvTokenBalance.text = it
            }
            fetchTokenInfoWithAddress(contractAddress, tokenIcon)
        }
        with(binding) {
            ivTokenIcon.gone()
            btnAdd.isEnabled = false
            ivClose.setOnClickListener { dismiss() }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        approveCallback?.invoke(false)
    }

    override fun onDestroy() {
        approveCallback = null
        super.onDestroy()
    }

    companion object {

        private const val EXTRA_CONTRACT_ADDRESS = "extra_contract_address"
        private const val EXTRA_TOKEN_ICON = "extra_token_icon"

        private var approveCallback: ((isApprove: Boolean) -> Unit)? = null

        fun observe(callback: (isApprove: Boolean) -> Unit) {
            this.approveCallback = callback
        }

        fun show(fragmentManager: FragmentManager, contractAddress: String, tokenIcon: String? = null) {
            AddCustomTokenDialog().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_CONTRACT_ADDRESS, contractAddress)
                    putString(EXTRA_TOKEN_ICON, tokenIcon)
                }
            }.show(fragmentManager, "")
        }
    }

}