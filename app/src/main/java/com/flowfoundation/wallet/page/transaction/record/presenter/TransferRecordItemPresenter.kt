package com.flowfoundation.wallet.page.transaction.record.presenter

import android.annotation.SuppressLint
import android.view.View
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.databinding.ItemTransferRecordBinding
import com.flowfoundation.wallet.network.model.TransferRecord
import com.flowfoundation.wallet.network.model.TransferRecord.Companion.TRANSFER_TYPE_SEND
import com.flowfoundation.wallet.page.browser.openInFlowScan
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.toSafeFloat
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.formatNum
import org.joda.time.format.ISODateTimeFormat
import java.math.RoundingMode

class TransferRecordItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<TransferRecord> {

    private val binding by lazy { ItemTransferRecordBinding.bind(view) }

    override fun bind(model: TransferRecord) {
        with(binding) {
            if (model.image.isNullOrBlank()) {
                iconView.setImageResource(R.drawable.ic_transaction_default)
            } else {
                Glide.with(iconView).load(model.image).into(iconView)
            }
            transferTypeView.rotation = if (model.transferType == TRANSFER_TYPE_SEND) 0.0f else 180.0f
//            val title = model.token?.replaceBeforeLast(".", "")?.removePrefix(".")
//            titleView.text = if (title.isNullOrBlank()) {
//                model.title
//            } else {
//                title
//            }
            titleView.text = model.title ?: ""
//            val amount = if (model.amount.isNullOrBlank()) "" else (model.amount.toSafeFloat() / 100000000f).formatNum(8, RoundingMode.HALF_UP)
            amountView.text = model.amount ?: ""
            bindStatus(model)
            bindTime(model)
            bindAddress(model)
        }

        binding.root.setOnClickListener { openInFlowScan(findActivity(view)!!, model.txid!!) }
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
        statusView.text = transfer.status
    }

    @SuppressLint("StringFormatInvalid")
    private fun ItemTransferRecordBinding.bindAddress(transfer: TransferRecord) {
        val str = if (transfer.transferType == TRANSFER_TYPE_SEND) {
            view.context.getString(R.string.to_address, transfer.receiver)
        } else if (!transfer.sender.isNullOrEmpty()) {
            view.context.getString(R.string.from_address, transfer.sender)
        } else ""
        toView.text = str
    }

}