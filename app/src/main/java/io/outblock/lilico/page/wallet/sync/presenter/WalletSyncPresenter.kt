package io.outblock.lilico.page.wallet.sync.presenter

import android.graphics.Bitmap
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.databinding.ActivitySyncWalletBinding
import io.outblock.lilico.page.wallet.sync.model.SyncReceiveModel


class WalletSyncPresenter(
    private val binding: ActivitySyncWalletBinding
) : BasePresenter<SyncReceiveModel> {

    override fun bind(model: SyncReceiveModel) {
        model.qrCode?.let { updateQRCode(it) }
    }

    private fun updateQRCode(qrCode: Bitmap) {
        binding.ivQrCode.setImageBitmap(qrCode)
    }

}