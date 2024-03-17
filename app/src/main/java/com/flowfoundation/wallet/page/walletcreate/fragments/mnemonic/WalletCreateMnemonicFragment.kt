package com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.instabug.library.Instabug
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentWalletCreateMnemonicBinding
import com.flowfoundation.wallet.page.walletcreate.WALLET_CREATE_STEP_CLOUD_PWD
import com.flowfoundation.wallet.page.walletcreate.WALLET_CREATE_STEP_MNEMONIC_CHECK
import com.flowfoundation.wallet.page.walletcreate.WalletCreateViewModel
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.setRegistered
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.wallet.Wallet
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration

class WalletCreateMnemonicFragment : Fragment() {

    private lateinit var binding: FragmentWalletCreateMnemonicBinding

    private lateinit var viewModel: WalletCreateMnemonicViewModel

    private val pageViewModel by lazy { ViewModelProvider(requireActivity())[WalletCreateViewModel::class.java] }

    private val adapter = MnemonicAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWalletCreateMnemonicBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.title.text = SpannableString(R.string.recovery_phrase.res2String()).apply {
            val protection = R.string.phrase.res2String()
            val index = indexOf(protection)
            setSpan(ForegroundColorSpan(R.color.colorSecondary.res2color()), index, index + protection.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        with(binding.mnemonicContainer) {
            adapter = this@WalletCreateMnemonicFragment.adapter
            layoutManager = GridLayoutManager(requireContext(), 2, GridLayoutManager.VERTICAL, false)
            addItemDecoration(GridSpaceItemDecoration(vertical = 16.0))
            Instabug.addPrivateViews(this)
        }

        with(binding) {
            backupCloud.setOnClickListener { pageViewModel.changeStep(WALLET_CREATE_STEP_CLOUD_PWD) }
            backupManually.setOnClickListener { pageViewModel.changeStep(WALLET_CREATE_STEP_MNEMONIC_CHECK) }
            copyButton.setOnClickListener {
                textToClipboard(Wallet.store().mnemonic())
                toast(msgRes = R.string.copied_to_clipboard)
            }
        }

        viewModel = ViewModelProvider(this)[WalletCreateMnemonicViewModel::class.java].apply {
            mnemonicList.observe(viewLifecycleOwner) { adapter.setNewDiffData(it) }
            loadMnemonic()
        }
        setRegistered()
    }
}