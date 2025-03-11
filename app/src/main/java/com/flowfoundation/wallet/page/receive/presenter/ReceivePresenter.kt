package com.flowfoundation.wallet.page.receive.presenter

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import androidx.core.view.drawToBitmap
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityReceiveBinding
import com.flowfoundation.wallet.manager.evm.EVMWalletManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.receive.ReceiveActivity
import com.flowfoundation.wallet.page.receive.model.ReceiveData
import com.flowfoundation.wallet.page.receive.model.ReceiveModel
import com.flowfoundation.wallet.utils.CACHE_PATH
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.saveToFile
import com.flowfoundation.wallet.utils.shareFile
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toQRDrawable
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.ProgressDialog
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import java.io.File

class ReceivePresenter(
    private val activity: ReceiveActivity,
    private val binding: ActivityReceiveBinding,
) : BasePresenter<ReceiveModel> {

    private var cadenceQRCode: Drawable? = null
    private var evmQRCode: Drawable? = null
    private var receiveData: ReceiveData? = null

    private var isImageLoaded = false

    init {
        setupToolbar()
        binding.root.addStatusBarTopPadding()
    }

    override fun bind(model: ReceiveModel) {
        model.data?.let { updateWallet(it) }
        model.qrcode?.let { updateQrcode(it) }
    }

    @SuppressLint("SetTextI18n")
    private fun updateWallet(data: ReceiveData) {
        receiveData = data
        with(binding) {
            tvWalletName.text = data.walletName.ifBlank { R.string.wallet.res2String() }
            tvWalletAddress.text = data.address.toAddress()
            shareButton.setOnClickListener {
                try {
                    showShareQRCode()
                } catch (e: Exception) {
                    copy()
                }
            }
            tvCurrentVm.setVisible(EVMWalletManager.haveEVMAddress())
            switchVmLayout.setVisible(EVMWalletManager.haveEVMAddress())
            switchVmLayout.setOnVMSwitchListener { isSwitchToEVM ->
                updateTextAndQRCode(isSwitchToEVM)
            }
            btnCopy.setOnClickListener { copy() }
        }
    }

    private fun copy() {
        textToClipboard(binding.tvWalletAddress.text.toString().trim())
        toast(msgRes = R.string.copy_address_toast)
    }

    private fun showShareQRCode() {
        if (isImageLoaded) {
            val dialog = ProgressDialog(activity)
            dialog.show()
            ioScope {
                val bitmap = binding.ivQrCode.drawToBitmap()
                val file = File(CACHE_PATH, "qrcode_share.jpg")
                bitmap.saveToFile(file)
                uiScope {
                    dialog.dismiss()
                    activity.shareFile(file, title = "QRCode share", text = binding.tvWalletAddress.text.toString(), type = "image/*")
                }
            }
        }
    }

    private fun updateTextAndQRCode(isSwitchToEVM: Boolean) {
        with(binding) {
            tvWalletAddress.text = if (isSwitchToEVM) {
                EVMWalletManager.getEVMAddress()
            } else {
                receiveData?.address?.toAddress()
            }
            if (isSwitchToEVM && evmQRCode != null) {
                ivQrCode.setImageDrawable(evmQRCode)
            } else {
                ivQrCode.setImageDrawable(cadenceQRCode)
            }
            clEvmTip.setVisible(isSwitchToEVM)
        }
        isImageLoaded = true
    }

    private fun updateQrcode(qrcode: Drawable) {
        isImageLoaded = false
        cadenceQRCode = qrcode
        evmQRCode = EVMWalletManager.getEVMAddress()?.toAddress()?.toQRDrawable(isEVM = true)
        updateTextAndQRCode(WalletManager.isEVMAccountSelected())
    }

    private fun setupToolbar() {
        binding.toolbar.navigationIcon?.mutate()?.setTint(R.color.neutrals1.res2color())
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setDisplayShowHomeEnabled(true)
        activity.title = R.string.receive.res2String()
    }
}