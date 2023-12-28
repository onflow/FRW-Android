package io.outblock.lilico.page.backup.multibackup.presenter

import android.annotation.SuppressLint
import androidx.transition.Fade
import androidx.transition.Transition
import com.google.android.material.transition.MaterialSharedAxis
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.page.backup.multibackup.MultiBackupActivity
import io.outblock.lilico.page.backup.multibackup.fragment.BackupStartFragment
import io.outblock.lilico.page.backup.multibackup.model.BackupOption
import io.outblock.lilico.page.backup.multibackup.model.BackupOptionModel


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
            BackupOption.BACKUP_START -> BackupStartFragment()
            BackupOption.BACKUP_WITH_GOOGLE_DRIVE -> TODO()
            BackupOption.BACKUP_WITH_RECOVERY_PHRASE -> TODO()
            BackupOption.BACKUP_COMPLETED -> TODO()
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