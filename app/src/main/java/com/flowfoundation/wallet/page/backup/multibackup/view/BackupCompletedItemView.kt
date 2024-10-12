package com.flowfoundation.wallet.page.backup.multibackup.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.LayoutBackupCompletedItemBinding
import com.flowfoundation.wallet.manager.account.DeviceInfoManager
import com.flowfoundation.wallet.network.model.LocationInfo
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupCompletedItem
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicModel
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicWhiteSerialAdapter
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration
import com.instabug.library.Instabug


class BackupCompletedItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = LayoutBackupCompletedItemBinding.inflate(LayoutInflater.from(context))
    private val adapter by lazy { MnemonicWhiteSerialAdapter() }

    init {
        addView(binding.root)
    }

    fun setItemInfo(item: BackupCompletedItem, locationInfo: LocationInfo?, isSuccess: Boolean?) {
        with(binding) {
            ivBackupType.setImageResource(item.type.iconRes)
            tvBackupName.text = item.type.displayName
            tvBackupOs.text = DeviceInfoManager.getDeviceInfoUserAgent()
            tvBackupLocation.text = cityInfo(locationInfo?.city, locationInfo?.countryCode)
            ivBackupMark.setImageResource(
                if (isSuccess == true) {
                    R.drawable.ic_circle_right_green
                } else {
                    R.drawable.ic_username_error
                }
            )
        }
        if (item.type == BackupType.MANUAL) {
            val list =
                item.mnemonic.split(" ").mapIndexed { index, s -> MnemonicModel(index + 1, s) }
            val result = mutableListOf<MnemonicModel>()
            val mid = list.size / 2 + 1
            (0 until mid).forEach { i ->
                result.add(list[i])
                val j = i + mid
                if (j < list.size) {
                    result.add(list[j])
                }
            }
            if (result.isNotEmpty()) {
                with(binding.mnemonicContainer) {
                    adapter = this@BackupCompletedItemView.adapter
                    layoutManager = GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false)
                    addItemDecoration(GridSpaceItemDecoration(vertical = 16.0))
                    Instabug.addPrivateViews(this)
                }
                binding.clMnemonic.visible()
                binding.mnemonicContainer.visible()
                binding.copyButton.setOnClickListener {
                    textToClipboard(item.mnemonic)
                    toast(R.string.copied_to_clipboard)
                }
                adapter.setNewDiffData(result)
            }
        }
    }

    private fun cityInfo(city: String?, country: String?): String {
        val sb = StringBuilder()
        if (city.isNullOrBlank()) {
            if (!country.isNullOrBlank()) {
                sb.append(country)
            }
        } else {
            sb.append(city)
            if (!country.isNullOrBlank()) {
                sb.append(", ").append(country)
            }
        }
        return sb.toString()
    }
}