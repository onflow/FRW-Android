package io.outblock.lilico.page.send.nft

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.journeyapps.barcodescanner.ScanOptions
import io.outblock.lilico.R
import io.outblock.lilico.databinding.DialogSendNftAddressBinding
import io.outblock.lilico.firebase.analytics.reportEvent
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.model.AddressBookContact
import io.outblock.lilico.page.address.AddressBookFragment
import io.outblock.lilico.page.address.AddressBookViewModel
import io.outblock.lilico.page.nft.nftlist.nftWalletAddress
import io.outblock.lilico.page.nft.nftlist.utils.NftCache
import io.outblock.lilico.page.send.nft.confirm.NftSendConfirmDialog
import io.outblock.lilico.page.send.transaction.SelectSendAddressViewModel
import io.outblock.lilico.page.send.transaction.model.TransactionSendModel
import io.outblock.lilico.page.send.transaction.presenter.TransactionSendPresenter
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.launch
import io.outblock.lilico.utils.registerBarcodeLauncher
import io.outblock.lilico.utils.uiScope

class NftSendAddressDialog : BottomSheetDialogFragment() {
    private val nft by lazy { NftCache(nftWalletAddress()).findNftById(arguments?.getString(EXTRA_ID)!!) }

    private lateinit var binding: DialogSendNftAddressBinding
    private lateinit var presenter: TransactionSendPresenter
    private lateinit var viewModel: SelectSendAddressViewModel
    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        barcodeLauncher = registerBarcodeLauncher { presenter.bind(TransactionSendModel(qrcode = it)) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
        if (WalletManager.isChildAccountSelected()) {
            return
        }
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
                        fromAddress = WalletManager.selectedWalletAddress(),
                    )
                ).show(activity.supportFragmentManager, "")
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "extra_nft"

        fun newInstance(nftUniqueId: String): NftSendAddressDialog {
            return NftSendAddressDialog().apply {
                arguments = Bundle().apply { putString(EXTRA_ID, nftUniqueId) }
            }
        }
    }
}