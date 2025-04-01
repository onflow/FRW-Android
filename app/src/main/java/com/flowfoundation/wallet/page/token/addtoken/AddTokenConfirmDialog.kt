package com.flowfoundation.wallet.page.token.addtoken

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.databinding.DialogAddTokenConfirmBinding
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.utils.uiScope

class AddTokenConfirmDialog : BottomSheetDialogFragment() {

    private val coin by lazy { arguments?.getParcelable<FlowCoin>(EXTRA_TOKEN)!! }

    private lateinit var binding: DialogAddTokenConfirmBinding

    private lateinit var viewModel: AddTokenViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogAddTokenConfirmBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity())[AddTokenViewModel::class.java].apply {
            cadenceExecuteLiveData.observe(this@AddTokenConfirmDialog) {
                if (it) {
                    dismiss()
                    cadenceExecuteLiveData.value = false
                }
            }
        }

        binding.closeButton.setOnClickListener { dismiss() }
        with(binding) {
            tokenNameView.text = coin.name
            Glide.with(iconView).load(coin.icon()).into(iconView)
            actionButton.setOnClickListener {
                uiScope {
                    actionButton.setProgressVisible(true)
                    viewModel.addToken(coin)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TOKEN = "extra_token"

        fun show(fragmentManager: FragmentManager, coin: FlowCoin) {
            AddTokenConfirmDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_TOKEN, coin)
                }
            }.show(fragmentManager, "")
        }
    }
}