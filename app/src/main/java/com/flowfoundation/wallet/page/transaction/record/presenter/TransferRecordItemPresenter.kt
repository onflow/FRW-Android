package com.flowfoundation.wallet.page.transaction.record.presenter

import android.annotation.SuppressLint
import android.view.View
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemTransferRecordBinding
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.TransferRecord
import com.flowfoundation.wallet.network.model.TransferRecord.Companion.TRANSFER_TYPE_SEND
import com.flowfoundation.wallet.page.browser.openInFlowEVMScan
import com.flowfoundation.wallet.page.browser.openInFlowScan
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.shortenEVMString
import com.flowfoundation.wallet.utils.svgToPng
import org.joda.time.format.ISODateTimeFormat

class TransferRecordItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<TransferRecord> {

    private val binding by lazy { ItemTransferRecordBinding.bind(view) }

    override fun bind(model: TransferRecord) {
        with(binding) {
            if (model.logo().isEmpty()) {
                iconView.setImageResource(R.drawable.ic_transaction_default)
            } else {
                Glide.with(iconView).load(model.logo().svgToPng()).into(iconView)
            }
            transferTypeView.rotation = if (model.transferType == TRANSFER_TYPE_SEND) 0.0f else 180.0f
            titleView.text = model.title ?: ""
            amountView.text = model.amount ?: ""
            bindStatus(model)
            bindTime(model)
            bindAddress(model)
        }

        binding.root.setOnClickListener {
            findActivity(view)?.let {
                if (WalletManager.isEVMAccountSelected()) {
                    openInFlowEVMScan(it, model.txid.orEmpty())
                    return@setOnClickListener
                }
                openInFlowScan(it, model.txid.orEmpty())
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun ItemTransferRecordBinding.bindTime(transfer: TransferRecord) {
        if (transfer.time.isNullOrBlank()) {
            timeView.text = ""
        } else timeView.text = ISODateTimeFormat.dateTimeParser().parseDateTime(transfer.time).toString("MMM dd")
    }

    private fun ItemTransferRecordBinding.bindStatus(transfer: TransferRecord) {
        val color = when (transfer.status.orEmpty()) {
            "Sealed" -> if (transfer.error == true) R.color.warning2.res2color() else R.color.success3.res2color()
            else -> R.color.neutrals6.res2color()
        }

        statusView.setTextColor(color)
        statusView.text = transfer.status?.toUpperCase(Locale.current) ?: ""
    }

    @SuppressLint("StringFormatInvalid")
    private fun ItemTransferRecordBinding.bindAddress(transfer: TransferRecord) {
        val str = if (transfer.transferType == TRANSFER_TYPE_SEND) {
            if (transfer.receiver.isNullOrEmpty()) {
                ""
            } else {
                view.context.getString(R.string.to_address, shortenEVMString(transfer.receiver))
            }
        } else if (!transfer.sender.isNullOrEmpty()) {
            view.context.getString(R.string.from_address, shortenEVMString(transfer.sender))
        } else ""
        toView.text = str
    }

}