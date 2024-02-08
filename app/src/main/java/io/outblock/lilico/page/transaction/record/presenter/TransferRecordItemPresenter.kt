package io.outblock.lilico.page.transaction.record.presenter

import android.annotation.SuppressLint
import android.view.View
import com.bumptech.glide.Glide
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.base.recyclerview.BaseViewHolder
import io.outblock.lilico.databinding.ItemTransferRecordBinding
import io.outblock.lilico.network.model.TransferRecord
import io.outblock.lilico.network.model.TransferRecord.Companion.TRANSFER_TYPE_SEND
import io.outblock.lilico.page.browser.openInFlowScan
import io.outblock.lilico.utils.extensions.res2color
import io.outblock.lilico.utils.extensions.toSafeFloat
import io.outblock.lilico.utils.findActivity
import io.outblock.lilico.utils.formatNum
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
            val title = model.token?.replaceBeforeLast(".", "")?.removePrefix(".")
            titleView.text = if (title.isNullOrBlank()) {
                model.title
            } else {
                title
            }
            val amount = if (model.amount.isNullOrBlank()) "" else (model.amount.toSafeFloat() / 100000000f).formatNum(8, RoundingMode.HALF_UP)
            amountView.text = amount
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
            view.context.getString(R.string.from, transfer.sender)
        } else ""
        toView.text = str
    }

}