package com.flowfoundation.wallet.page.wallet

import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.fragment.BaseFragment
import com.flowfoundation.wallet.databinding.FragmentCoordinatorWalletBinding
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.notification.OnNotificationUpdate
import com.flowfoundation.wallet.manager.notification.WalletNotificationManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.manager.wallpaper.OnWallpaperChange
import com.flowfoundation.wallet.manager.wallpaper.WallpaperManager
import com.flowfoundation.wallet.page.dialog.common.BackupTipsDialog
import com.flowfoundation.wallet.page.evm.EnableEVMActivity
import com.flowfoundation.wallet.page.scan.dispatchScanResult
import com.flowfoundation.wallet.page.transaction.record.TransactionRecordActivity
import com.flowfoundation.wallet.page.wallet.dialog.MoveDialog
import com.flowfoundation.wallet.page.wallet.model.WalletCoinItemModel
import com.flowfoundation.wallet.page.wallet.model.WalletFragmentModel
import com.flowfoundation.wallet.page.wallet.presenter.WalletFragmentPresenter
import com.flowfoundation.wallet.page.wallet.presenter.WalletHeaderPresenter
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.isBackupGoogleDrive
import com.flowfoundation.wallet.utils.isBackupManually
import com.flowfoundation.wallet.utils.isMultiBackupCreated
import com.flowfoundation.wallet.utils.isShowBackupDialog
import com.flowfoundation.wallet.utils.launch
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.registerBarcodeLauncher
import com.flowfoundation.wallet.utils.uiScope
import com.journeyapps.barcodescanner.ScanOptions
import java.math.BigDecimal
import kotlin.math.abs

class WalletFragment : BaseFragment(), OnNotificationUpdate, OnWallpaperChange {

    private lateinit var binding: FragmentCoordinatorWalletBinding
    private lateinit var viewModel: WalletFragmentViewModel
    private lateinit var presenter: WalletFragmentPresenter
    private lateinit var headerPresenter: WalletHeaderPresenter

    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>

    private var isBackupShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        barcodeLauncher = registerBarcodeLauncher { result -> dispatchScanResult(requireContext(), result.orEmpty()) }
        WalletNotificationManager.addListener(this)
        WallpaperManager.addListener(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCoordinatorWalletBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        presenter = WalletFragmentPresenter(this, binding)
        headerPresenter = WalletHeaderPresenter(this, binding.walletHeader.root)
//        headerPlaceholderPresenter = WalletHeaderPlaceholderPresenter(binding.shimmerPlaceHolder.root)

        binding.ivScan.setOnClickListener { barcodeLauncher.launch() }
        TransitionManager.beginDelayedTransition(binding.root)

        viewModel = ViewModelProvider(this)[WalletFragmentViewModel::class.java].apply {
            dataListLiveData.observe(viewLifecycleOwner) {
                presenter.bind(WalletFragmentModel(data = it))
                checkBackUp(it)
            }
            headerLiveData.observe(viewLifecycleOwner) { headerModel ->
                headerPresenter.bind(headerModel)
//                headerPlaceholderPresenter.bind(headerModel == null)
            }
//            clearDataList()
        }

        binding.appBarLayout.addOnOffsetChangedListener { _, verticalOffset ->
            val scrollRange = (251 - 104).toFloat().dp2px()
            logd("offset", "verticalOffset::$verticalOffset, scrollRange::$scrollRange")
            val offset = abs(verticalOffset).toFloat() / scrollRange
            val color = interpolateColor(R.color.transparent.res2color(), R.color.home_page_background.res2color(), offset.coerceIn(0f, 1f))
            binding.viewBackground.setBackgroundColor(color)
        }
    }

    private fun interpolateColor(colorStart: Int, colorEnd: Int, factor: Float): Int {
        val startA = (colorStart shr 24) and 0xff
        val startR = (colorStart shr 16) and 0xff
        val startG = (colorStart shr 8) and 0xff
        val startB = colorStart and 0xff

        val endA = (colorEnd shr 24) and 0xff
        val endR = (colorEnd shr 16) and 0xff
        val endG = (colorEnd shr 8) and 0xff
        val endB = colorEnd and 0xff

        val a = (startA + (factor * (endA - startA)).toInt()) and 0xff
        val r = (startR + (factor * (endR - startR)).toInt()) and 0xff
        val g = (startG + (factor * (endG - startG)).toInt()) and 0xff
        val b = (startB + (factor * (endB - startB)).toInt()) and 0xff

        return (a shl 24) or (r shl 16) or (g shl 8) or b
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
                val sumCoin = coinList.map { it.balance }.fold(BigDecimal.ZERO) { sum, balance -> sum + balance }
                if (sumCoin > BigDecimal(0.001)) {
                    isBackupShown = true
                    if (isShowBackupDialog()) {
                        BackupTipsDialog.show(childFragmentManager)
                    }
                } else {
                    isBackupShown = false
                }
            }
//            if (BackupTipManager.isShowBackupTip()) {
//                val sumCoin = coinList.map { it.balance }.sum()
//                if (sumCoin > 0.001f) {
//                    isBackupShown = true
//                    BackupTipsDialog.show(childFragmentManager)
//                } else {
//                    isBackupShown = false
//                }
//            } else {
//                isBackupShown = false
//            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    override fun onNotificationUpdate() {
        binding.notificationView.onNotificationChange()
    }

    override fun onWallpaperChange(id: Int, position: Int, previousPosition: Int) {
        presenter.onWallpaperChange(id)
    }
}