package com.flowfoundation.wallet.page.backup.multibackup.presenter

import android.annotation.SuppressLint
import androidx.transition.Fade
import androidx.transition.Transition
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupRecoveryPhraseFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupRecoveryPhraseInfoFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupRecoveryPhraseWarningFragment
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupRecoveryPhraseOption
import com.google.android.material.transition.MaterialSharedAxis


class BackupRecoveryPhrasePresenter(private val parentFragment: BackupRecoveryPhraseFragment) :
    BasePresenter<BackupRecoveryPhraseOption> {
    private var currentModel: BackupRecoveryPhraseOption? = null

    override fun bind(model: BackupRecoveryPhraseOption) {
        onOptionChange(model)
    }

    @SuppressLint("CommitTransaction")
    private fun onOptionChange(model: BackupRecoveryPhraseOption) {
        val transition = createTransition(currentModel, model)
        val fragment = when (model) {
            BackupRecoveryPhraseOption.BACKUP_WARING -> BackupRecoveryPhraseWarningFragment()
            BackupRecoveryPhraseOption.BACKUP_RECOVERY_PHRASE -> BackupRecoveryPhraseInfoFragment()
        }
        fragment.enterTransition = transition
        parentFragment.childFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        currentModel = model
    }

    private fun createTransition(
        currentModel: BackupRecoveryPhraseOption?,
        model: BackupRecoveryPhraseOption
    ): Transition {
        if (currentModel == null) {
            return Fade().apply { duration = 50 }
        }
        val transition = MaterialSharedAxis(MaterialSharedAxis.X, true)

        transition.addTarget(currentModel.layoutId)
        transition.addTarget(model.layoutId)
        return transition
    }
}