package com.flowfoundation.wallet.page.send.nft

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.journeyapps.barcodescanner.ScanOptions
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogSendNftAddressBinding
import com.flowfoundation.wallet.firebase.analytics.reportEvent
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.address.AddressBookFragment
import com.flowfoundation.wallet.page.address.AddressBookViewModel
import com.flowfoundation.wallet.page.nft.nftlist.nftWalletAddress
import com.flowfoundation.wallet.page.nft.nftlist.utils.NftCache
import com.flowfoundation.wallet.page.send.nft.confirm.NftSendConfirmDialog
import com.flowfoundation.wallet.page.send.transaction.SelectSendAddressViewModel
import com.flowfoundation.wallet.page.send.transaction.model.TransactionSendModel
import com.flowfoundation.wallet.page.send.transaction.presenter.TransactionSendPresenter
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.launch
import com.flowfoundation.wallet.utils.registerBarcodeLauncher
import com.flowfoundation.wallet.utils.uiScope

class NftSendAddressDialog : BottomSheetDialogFragment() {
    private val nft by lazy {
        NftCache(nftWalletAddress()).findNFTByIdAndContractName(
            arguments?.getString(EXTRA_ID)!!,
            arguments?.getString(EXTRA_CONTRACT_NAME)
        )
    }
    private val fromAddress by lazy { arguments?.getString(EXTRA_FROM_ADDRESS) ?: WalletManager.selectedWalletAddress() }

    private lateinit var binding: DialogSendNftAddressBinding
    private lateinit var presenter: TransactionSendPresenter
    private lateinit var viewModel: SelectSendAddressViewModel
    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        barcodeLauncher = registerBarcodeLauncher { presenter.bind(TransactionSendModel(qrcode = it)) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSendNftAddressBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        reportEvent("page_nft_send_address_dialog")
        childFragmentManager.beginTransaction().replace(R.id.search_container, AddressBookFragment()).commit()

        presenter = TransactionSendPresenter(childFragmentManager, binding.addressContent)
        viewModel = ViewModelProvider(requireActivity())[SelectSendAddressViewModel::class.java].apply {
            onAddressSelectedLiveData.observe(viewLifecycleOwner) { onAddressSelected(it) }
        }
        ViewModelProvider(requireActivity())[AddressBookViewModel::class.java].apply {
            clearEditTextFocusLiveData.observe(this@NftSendAddressDialog) { presenter.bind(TransactionSendModel(isClearInputFocus = it)) }
        }
        binding.closeButton.setOnClickListener { dismiss() }
        binding.scanButton.setOnClickListener { barcodeLauncher.launch() }
    }

    override fun onDestroy() {
        viewModel.onAddressSelectedLiveData.postValue(null)
        super.onDestroy()
    }

    private fun onAddressSelected(contact: AddressBookContact?) {
        contact ?: return
        ioScope {
            val nft = this@NftSendAddressDialog.nft ?: return@ioScope
            uiScope {
                val activity = requireActivity()
                dismiss()

                NftSendConfirmDialog.newInstance(
                    NftSendModel(
                        nft = nft,
                        target = contact,
                        fromAddress = fromAddress,
                    )
                ).show(activity.supportFragmentManager, "")
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "extra_nft"
        private const val EXTRA_CONTRACT_NAME = "extra_contract_name"
        private const val EXTRA_FROM_ADDRESS = "extra_from_address"

        fun newInstance(nftUniqueId: String, fromAddress: String, contractName: String?): NftSendAddressDialog {
            return NftSendAddressDialog().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_ID, nftUniqueId)
                    putString(EXTRA_CONTRACT_NAME, contractName ?: "")
                    putString(EXTRA_FROM_ADDRESS, fromAddress)
                }
            }
        }
    }
}