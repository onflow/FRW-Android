package com.flowfoundation.wallet.page.backup.multibackup.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.databinding.ViewBackupOptionBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOption
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreOption
import com.flowfoundation.wallet.utils.extensions.visible


class BackupOptionView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ViewBackupOptionBinding.inflate(LayoutInflater.from(context))

    init {
        addView(binding.root)
    }

    fun setBackupOptionList(list: List<BackupOption>) {
        with(binding) {
            val optionList = list.filter { it != BackupOption.BACKUP_COMPLETED }
            optionList.forEachIndexed { index, backupOption ->
                when (index) {
                    0 -> {
                        ivOptionIconFirst.setImageResource(backupOption.iconId)
                    }
                    1 -> {
                        ivOptionIconPlus.visible()
                        ivOptionIconSecond.setImageResource(backupOption.iconId)
                        ivOptionIconSecond.visible()
                    }
                    2 -> {
                        guideline.setGuidelinePercent(0.8f)
                        ivOptionIconThird.setImageResource(backupOption.iconId)
                        ivOptionIconThird.visible()
                    }
                }
            }
        }
    }

    fun setRestoreOptionList(list: List<RestoreOption>) {
        with(binding) {
            val optionList = list.filter { it != RestoreOption.RESTORE_COMPLETED }
            optionList.forEachIndexed { index, restoreOption ->
                when (index) {
                    0 -> {
                        ivOptionIconFirst.setImageResource(restoreOption.iconId)
                    }
                    1 -> {
                        ivOptionIconPlus.visible()
                        ivOptionIconSecond.setImageResource(restoreOption.iconId)
                        ivOptionIconSecond.visible()
                    }
                    2 -> {
                        guideline.setGuidelinePercent(0.8f)
                        ivOptionIconThird.setImageResource(restoreOption.iconId)
                        ivOptionIconThird.visible()
                    }
                }
            }
        }
    }
}