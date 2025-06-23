package com.flowfoundation.wallet.page.backup.multibackup.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.ViewBackupProgressBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOption
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible


class BackupProgressView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ViewBackupProgressBinding.inflate(LayoutInflater.from(context))

    init {
        addView(binding.root)
    }

    fun setProgressInfo(
        list: List<BackupOption>,
        currentOption: BackupOption,
        isCompleted: Boolean
    ) {
        val optionList = list.filter { it != BackupOption.BACKUP_COMPLETED }
        when (optionList.size) {
            1 -> handleSingleOption(option = optionList[0], isCompleted)
            2 -> handleTwoOptions(optionList, currentOption, isCompleted)
            3 -> handleThreeOptions(optionList, currentOption, isCompleted)
            else -> gone()
        }
    }

    private fun handleSingleOption(option: BackupOption, isCompleted: Boolean) {
        with(binding) {
            ivFirstOption.setImageResource(getBackupOptionIcon(option, isCompleted))
            if (isCompleted) {
                ivFirstSelected.visible()
                lineOne.setBackgroundResource(R.color.colorSecondary)
                ivCompleteOption.setImageResource(R.drawable.ic_backup_complete_green)
            } else {
                ivFirstSelected.gone()
                lineOne.setBackgroundResource(R.color.bg_3)
                ivCompleteOption.setImageResource(R.drawable.ic_backup_complete_gray)
            }
            groupSecond.gone()
            groupThird.gone()
        }
    }

    private fun handleTwoOptions(
        optionList: List<BackupOption>,
        currentOption: BackupOption,
        isCompleted: Boolean
    ) {
        val firstOption = optionList[0]
        val secondOption = optionList[1]
        with(binding) {
            groupSecond.visible()
            when (currentOption) {
                firstOption -> {
                    updateOptionUI(
                        firstOption,
                        secondOption,
                        isFirstCompleted = isCompleted,
                        isSecondCompleted = false
                    )
                    ivCompleteOption.setImageResource(R.drawable.ic_backup_complete_progress)
                }
                secondOption -> {
                    updateOptionUI(
                        firstOption,
                        secondOption,
                        isFirstCompleted = true,
                        isSecondCompleted = isCompleted
                    )
                    ivCompleteOption.setImageResource(
                        if (isCompleted) R.drawable.ic_backup_complete_green else R.drawable.ic_backup_complete_progress
                    )
                }
                else -> {}
            }
        }
    }

    private fun handleThreeOptions(
        optionList: List<BackupOption>,
        currentOption: BackupOption,
        isCompleted: Boolean
    ) {
        val firstOption = optionList[0]
        val secondOption = optionList[1]
        val thirdOption = optionList[2]
        with(binding) {
            groupSecond.visible()
            groupThird.visible()
            when (currentOption) {
                firstOption -> {
                    updateOptionUI(firstOption, secondOption, isCompleted, false)
                    updateThirdOption(thirdOption, false, R.color.bg_3)
                    ivCompleteOption.setImageResource(R.drawable.ic_backup_complete_progress)
                }
                secondOption -> {
                    updateOptionUI(firstOption, secondOption, true, isCompleted)
                    updateThirdOption(thirdOption, false, R.color.bg_3)
                    ivCompleteOption.setImageResource(R.drawable.ic_backup_complete_progress)
                }
                thirdOption -> {
                    updateOptionUI(
                        firstOption, secondOption,
                        isFirstCompleted = true,
                        isSecondCompleted = true
                    )
                    updateThirdOption(
                        thirdOption,
                        isCompleted,
                        if (isCompleted) R.color.colorSecondary else R.color.bg_3
                    )
                    ivCompleteOption.setImageResource(R.drawable.ic_backup_complete_progress)
                }
                else -> {}
            }
        }
    }

    private fun updateOptionUI(
        firstOption: BackupOption,
        secondOption: BackupOption,
        isFirstCompleted: Boolean,
        isSecondCompleted: Boolean
    ) {
        with(binding) {
            ivFirstOption.setImageResource(getBackupOptionIcon(firstOption, isFirstCompleted))
            ivFirstSelected.setVisible(isFirstCompleted)
            lineOne.setBackgroundResource(if (isFirstCompleted) R.color.colorSecondary else R.color.bg_3)

            ivSecondOption.setImageResource(getBackupOptionIcon(secondOption, isSecondCompleted))
            ivSecondSelected.setVisible(isSecondCompleted)
            lineTwo.setBackgroundResource(if (isSecondCompleted) R.color.colorSecondary else R.color.bg_3)
        }
    }

    private fun updateThirdOption(thirdOption: BackupOption, isCompleted: Boolean, lineColor: Int) {
        with(binding) {
            ivThirdOption.setImageResource(getBackupOptionIcon(thirdOption, isCompleted))
            ivThirdSelected.setVisible(isCompleted)
            lineThree.setBackgroundResource(lineColor)
        }
    }

    private fun getBackupOptionIcon(backupOption: BackupOption, isCompleted: Boolean): Int {
        return if (isCompleted) {
            backupOption.iconId
        } else {
            backupOption.progressIcon
        }
    }

}