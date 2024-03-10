package com.flowfoundation.wallet.page.receive.presenter

import android.annotation.SuppressLint
import android.graphics.Bitmap
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityReceiveBinding
import com.flowfoundation.wallet.page.receive.ReceiveActivity
import com.flowfoundation.wallet.page.receive.model.ReceiveData
import com.flowfoundation.wallet.page.receive.model.ReceiveModel
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.wallet.toAddress

class ReceivePresenter(
    private val activity: ReceiveActivity,
    private val binding: ActivityReceiveBinding,
) : BasePresenter<ReceiveModel> {

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
        fun copy() {
            textToClipboard(data.address)
            toast(msgRes = R.string.copy_address_toast)
        }

        with(binding) {
            walletNameView.text = data.walletName.ifBlank { R.string.wallet.res2String() }
            walletAddressView.text = "(${data.address.toAddress()})"
            copyButton.setOnClickListener { copy() }
            copyDataButton.setOnClickListener { copy() }
        }
    }

    private fun updateQrcode(qrcode: Bitmap) {
        binding.qrcodeImageView.setImageBitmap(qrcode)
    }

    private fun setupToolbar() {
        binding.toolbar.navigationIcon?.mutate()?.setTint(R.color.neutrals1.res2color())
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setDisplayShowHomeEnabled(true)
        activity.title = R.string.receive.res2String()
    }
}