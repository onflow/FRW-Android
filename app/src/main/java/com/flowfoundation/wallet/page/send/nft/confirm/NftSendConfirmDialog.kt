package com.flowfoundation.wallet.page.send.nft.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogSendConfirmBinding
import com.flowfoundation.wallet.firebase.analytics.reportEvent
import com.flowfoundation.wallet.page.send.nft.NftSendModel
import com.flowfoundation.wallet.page.send.nft.confirm.model.NftSendConfirmDialogModel
import com.flowfoundation.wallet.page.send.nft.confirm.presenter.NftSendConfirmPresenter
import com.flowfoundation.wallet.utils.safeRun
import com.google.gson.Gson

class NftSendConfirmDialog : BottomSheetDialogFragment() {

    private val nft by lazy { arguments?.getString(EXTRA_NFT) ?: "" }

    private lateinit var binding: DialogSendConfirmBinding
    private lateinit var presenter: NftSendConfirmPresenter
    private lateinit var viewModel: NftSendConfirmViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        reportEvent("page_nft_send_confirm_dialog")
        binding = DialogSendConfirmBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter = NftSendConfirmPresenter(this, binding)
        viewModel = ViewModelProvider(this)[NftSendConfirmViewModel::class.java].apply {
            Gson().fromJson(this@NftSendConfirmDialog.nft, NftSendModel::class.java)?.let {
                bindSendModel(it)
            }
            userInfoLiveData.observe(this@NftSendConfirmDialog) { presenter.bind(NftSendConfirmDialogModel(
                userInfo = it
            )) }
            resultLiveData.observe(this@NftSendConfirmDialog) { isSuccess ->
                presenter.bind(NftSendConfirmDialogModel(isSendSuccess = isSuccess))
                if (!isSuccess) {
                    Toast.makeText(requireContext(), R.string.common_error_hint, Toast.LENGTH_LONG).show()
                    dismiss()
                }
            }
            load()
        }
        binding.closeButton.setOnClickListener { dismiss() }
    }

    companion object {
        private const val EXTRA_NFT = "extra_nft"

        fun newInstance(nft: NftSendModel): NftSendConfirmDialog {
            return NftSendConfirmDialog().apply {
                safeRun {
                    arguments = Bundle().apply {
                        putString(EXTRA_NFT, Gson().toJson(nft))
                    }
                }
            }
        }
    }
}