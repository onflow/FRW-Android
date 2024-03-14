package com.flowfoundation.wallet.page.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModelProvider
import com.journeyapps.barcodescanner.ScanOptions
import com.zackratos.ultimatebarx.ultimatebarx.statusBarHeight
import com.flowfoundation.wallet.base.fragment.BaseFragment
import com.flowfoundation.wallet.databinding.FragmentWalletBinding
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.dialog.common.BackupTipsDialog
import com.flowfoundation.wallet.page.scan.dispatchScanResult
import com.flowfoundation.wallet.page.wallet.model.WalletCoinItemModel
import com.flowfoundation.wallet.page.wallet.model.WalletFragmentModel
import com.flowfoundation.wallet.page.wallet.presenter.WalletFragmentPresenter
import com.flowfoundation.wallet.page.wallet.presenter.WalletHeaderPlaceholderPresenter
import com.flowfoundation.wallet.page.wallet.presenter.WalletHeaderPresenter
import com.flowfoundation.wallet.utils.isBackupGoogleDrive
import com.flowfoundation.wallet.utils.isBackupManually
import com.flowfoundation.wallet.utils.isMultiBackupCreated
import com.flowfoundation.wallet.utils.launch
import com.flowfoundation.wallet.utils.registerBarcodeLauncher
import com.flowfoundation.wallet.utils.uiScope

class WalletFragment : BaseFragment() {

    private lateinit var binding: FragmentWalletBinding
    private lateinit var viewModel: WalletFragmentViewModel
    private lateinit var presenter: WalletFragmentPresenter
    private lateinit var headerPresenter: WalletHeaderPresenter
    private lateinit var headerPlaceholderPresenter: WalletHeaderPlaceholderPresenter

    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>

    private var isBackupShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        barcodeLauncher = registerBarcodeLauncher { result -> dispatchScanResult(requireContext(), result.orEmpty()) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentWalletBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding.root) { setPadding(0, statusBarHeight, 0, 0) }

        presenter = WalletFragmentPresenter(this, binding)
        headerPresenter = WalletHeaderPresenter(binding.walletHeader.root)
        headerPlaceholderPresenter = WalletHeaderPlaceholderPresenter(binding.shimmerPlaceHolder.root)

        binding.scanButton.setOnClickListener { barcodeLauncher.launch() }

        viewModel = ViewModelProvider(requireActivity())[WalletFragmentViewModel::class.java].apply {
            dataListLiveData.observe(viewLifecycleOwner) {
                presenter.bind(WalletFragmentModel(data = it))
                checkBackUp(it)
            }
            headerLiveData.observe(viewLifecycleOwner) { headerModel ->
                headerPresenter.bind(headerModel)
                headerPlaceholderPresenter.bind(headerModel == null)
            }
        }
    }

    private fun checkBackUp(coinList: List<WalletCoinItemModel>) {
        if (isBackupShown || WalletManager.isChildAccountSelected()) {
            return
        }
        isBackupShown = true
        uiScope {
            if (isBackupGoogleDrive() || isBackupManually() || isMultiBackupCreated()) {
                isBackupShown = false
            } else {
                val sumCoin = coinList.map { it.balance }.sum()
                if (sumCoin > 0.001f) {
                    isBackupShown = true
                    BackupTipsDialog.show(childFragmentManager)
                } else {
                    isBackupShown = false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}