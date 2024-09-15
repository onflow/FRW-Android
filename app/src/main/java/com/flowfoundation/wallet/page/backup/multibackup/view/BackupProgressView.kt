package com.flowfoundation.wallet.page.backup.multibackup.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.ViewBackupProgressBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOption
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.visible


class BackupProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ViewBackupProgressBinding.inflate(LayoutInflater.from(context))

    init {
        addView(binding.root)
    }

    fun setProgressInfo(currentOption: BackupOption, isCompleted: Boolean) {
        with(binding) {
            if (isCompleted) {
                ivSecondOption.setImageResource(R.drawable.ic_backup_recovery_phrase_green)
                lineOne.setBackgroundResource(R.color.accent_green)
                ivCompleteOption.setImageResource(R.drawable.ic_backup_complete_green)
                lineTwo.setBackgroundResource(R.color.accent_green)
            } else if (currentOption == BackupOption.BACKUP_WITH_RECOVERY_PHRASE) {
                ivSecondOption.setImageResource(R.drawable.ic_backup_recovery_phrase_green)
                lineOne.setBackgroundResource(R.color.accent_green)
                ivCompleteOption.setImageResource(R.drawable.ic_backup_complete_gray)
                lineTwo.setBackgroundResource(R.color.bg_3)
            } else {
                ivSecondOption.setImageResource(R.drawable.ic_backup_recovery_phrase_gray)
                lineOne.setBackgroundResource(R.color.bg_3)
                ivCompleteOption.setImageResource(R.drawable.ic_backup_complete_gray)
                lineTwo.setBackgroundResource(R.color.bg_3)
            }
        }
    }
}