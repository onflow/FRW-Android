package io.outblock.lilico.page.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModelProvider
import com.journeyapps.barcodescanner.ScanOptions
import com.zackratos.ultimatebarx.ultimatebarx.statusBarHeight
import io.outblock.lilico.base.fragment.BaseFragment
import io.outblock.lilico.databinding.FragmentWalletBinding
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.page.dialog.common.BackupTipsDialog
import io.outblock.lilico.page.scan.dispatchScanResult
import io.outblock.lilico.page.wallet.model.WalletCoinItemModel
import io.outblock.lilico.page.wallet.model.WalletFragmentModel
import io.outblock.lilico.page.wallet.presenter.WalletFragmentPresenter
import io.outblock.lilico.page.wallet.presenter.WalletHeaderPlaceholderPresenter
import io.outblock.lilico.page.wallet.presenter.WalletHeaderPresenter
import io.outblock.lilico.utils.isBackupGoogleDrive
import io.outblock.lilico.utils.isBackupManually
import io.outblock.lilico.utils.launch
import io.outblock.lilico.utils.registerBarcodeLauncher
import io.outblock.lilico.utils.uiScope

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
            if (!isBackupGoogleDrive() && !isBackupManually()) {
                val sumCoin = coinList.map { it.balance }.sum()
                if (sumCoin > 0.001f) {
                    isBackupShown = true
                    BackupTipsDialog.show(childFragmentManager)
                } else {
                    isBackupShown = false
                }
            } else {
                isBackupShown = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}