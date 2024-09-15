package com.flowfoundation.wallet.page.backup.presenter

import android.annotation.SuppressLint
import androidx.transition.Fade
import androidx.transition.Transition
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.page.backup.BackupRecoveryPhraseActivity
import com.flowfoundation.wallet.page.backup.fragment.BackupSeedPhraseInfoFragment
import com.flowfoundation.wallet.page.backup.fragment.BackupSeedPhraseWarningFragment
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupSeedPhraseOption
import com.google.android.material.transition.MaterialSharedAxis


class BackupSeedPhrasePresenter(private val activity: BackupRecoveryPhraseActivity):
    BasePresenter<BackupSeedPhraseOption> {
    private var currentModel: BackupSeedPhraseOption? = null

    override fun bind(model: BackupSeedPhraseOption) {
        onOptionChange(model)
    }

    @SuppressLint("CommitTransaction")
    private fun onOptionChange(model: BackupSeedPhraseOption) {
        val transition = createTransition(currentModel, model)
        val fragment = when (model) {
            BackupSeedPhraseOption.BACKUP_WARING -> BackupSeedPhraseWarningFragment()
            BackupSeedPhraseOption.BACKUP_SEED_PHRASE -> BackupSeedPhraseInfoFragment()
        }
        fragment.enterTransition = transition
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        currentModel = model
    }

    private fun createTransition(
        currentModel: BackupSeedPhraseOption?,
        model: BackupSeedPhraseOption
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