package io.outblock.lilico.page.backup

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.outblock.lilico.R
import io.outblock.lilico.base.recyclerview.BaseAdapter
import io.outblock.lilico.base.recyclerview.BaseViewHolder
import io.outblock.lilico.page.backup.model.BackupKey
import io.outblock.lilico.page.backup.model.BackupListTitle
import io.outblock.lilico.page.backup.presenter.BackupListItemPresenter
import io.outblock.lilico.page.backup.presenter.BackupListTitlePresenter
import io.outblock.lilico.page.profile.subpage.wallet.device.model.DeviceModel
import io.outblock.lilico.page.profile.subpage.wallet.device.presenter.DeviceInfoPresenter

class BackupListAdapter : BaseAdapter<Any>(diffUtils) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is BackupListTitle -> TYPE_TITLE
            is BackupKey -> TYPE_BACKUP
            is DeviceModel -> TYPE_DEVICE
            else -> TYPE_NONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TITLE -> BackupListTitlePresenter(parent.inflate(R.layout.layout_backup_title_item))
            TYPE_BACKUP -> BackupListItemPresenter(parent.inflate(R.layout.layout_backup_info_item))
            TYPE_DEVICE -> DeviceInfoPresenter(parent.inflate(R.layout.layout_device_info_item))
            else -> BaseViewHolder(View(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is BackupListTitlePresenter -> holder.bind(getItem(position) as BackupListTitle)
            is BackupListItemPresenter -> holder.bind(getItem(position) as BackupKey)
            is DeviceInfoPresenter -> holder.bind(getItem(position) as DeviceModel)
        }
    }

    companion object {
        private const val TYPE_NONE = -1
        private const val TYPE_TITLE = 0
        private const val TYPE_BACKUP = 1
        private const val TYPE_DEVICE = 2
    }
}

private val diffUtils = object : DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is BackupKey && newItem is BackupKey) {
            return oldItem.keyId == newItem.keyId
        }
        if (oldItem is DeviceModel && newItem is DeviceModel) {
            return oldItem.id == newItem.id
        }
        return oldItem == newItem
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is BackupKey && newItem is BackupKey) {
            return oldItem.keyId == newItem.keyId
        }
        if (oldItem is DeviceModel && newItem is DeviceModel) {
            return oldItem.id == newItem.id
        }
        return oldItem == newItem
    }

}