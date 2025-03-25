package com.flowfoundation.wallet.page.backup.multibackup.presenter

import androidx.transition.Fade
import androidx.transition.Transition
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupStartAboutFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupStartFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupStartWithAboutFragment
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupStartOption
import com.google.android.material.transition.MaterialSharedAxis


class BackupStartWithAboutPresenter(private val parentFragment: BackupStartWithAboutFragment) :
    BasePresenter<BackupStartOption> {

    private var currentModel: BackupStartOption? = null

    override fun bind(model: BackupStartOption) {
        val transition = createTransition(currentModel, model)
        val fragment = when (model) {
            BackupStartOption.BACKUP_ABOUT -> BackupStartAboutFragment()
            BackupStartOption.BACKUP_START -> BackupStartFragment()
        }
        fragment.enterTransition = transition
        parentFragment.childFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        currentModel = model
    }

    private fun createTransition(
        currentModel: BackupStartOption?,
        model: BackupStartOption
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