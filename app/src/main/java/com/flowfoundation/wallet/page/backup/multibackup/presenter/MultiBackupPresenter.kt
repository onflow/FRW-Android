package com.flowfoundation.wallet.page.backup.multibackup.presenter

import android.annotation.SuppressLint
import androidx.transition.Fade
import androidx.transition.Transition
import com.google.android.material.transition.MaterialSharedAxis
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.page.backup.multibackup.MultiBackupActivity
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupCompletedFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupDropboxWithPinFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupGoogleDriveWithPinFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupRecoveryPhraseFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupStartWithAboutFragment
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOption
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOptionModel


class MultiBackupPresenter(private val activity: MultiBackupActivity) :
    BasePresenter<BackupOptionModel> {

    private var currentModel: BackupOptionModel? = null

    override fun bind(model: BackupOptionModel) {
        onOptionChange(model)
    }

    @SuppressLint("CommitTransaction")
    private fun onOptionChange(model: BackupOptionModel) {
        val transition = createTransition(currentModel, model)
        val fragment = when (model.option) {
            BackupOption.BACKUP_START -> BackupStartWithAboutFragment()
            BackupOption.BACKUP_WITH_GOOGLE_DRIVE -> BackupGoogleDriveWithPinFragment()
            BackupOption.BACKUP_WITH_RECOVERY_PHRASE -> BackupRecoveryPhraseFragment()
            BackupOption.BACKUP_WITH_DROPBOX -> BackupDropboxWithPinFragment()
            BackupOption.BACKUP_COMPLETED -> BackupCompletedFragment()
        }
        fragment.enterTransition = transition
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        currentModel = model
    }

    private fun createTransition(
        currentModel: BackupOptionModel?,
        model: BackupOptionModel
    ): Transition {
        if (currentModel == null) {
            return Fade().apply { duration = 50 }
        }
        val transition = MaterialSharedAxis(MaterialSharedAxis.X, currentModel.index < model.index)

        transition.addTarget(currentModel.option.layoutId)
        transition.addTarget(model.option.layoutId)
        return transition
    }
}