package com.flowfoundation.wallet.page.backup.presenter

import android.annotation.SuppressLint
import androidx.transition.Fade
import androidx.transition.Transition
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.page.backup.BackupViewMnemonicActivity
import com.flowfoundation.wallet.page.backup.fragment.ViewErrorFragment
import com.flowfoundation.wallet.page.backup.fragment.ViewPinCodeFragment
import com.flowfoundation.wallet.page.backup.fragment.ViewRecoveryPhraseFragment
import com.flowfoundation.wallet.page.backup.model.BackupViewMnemonicModel
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreErrorOption
import com.google.android.material.transition.MaterialSharedAxis


class BackupViewMnemonicPresenter(private val activity: BackupViewMnemonicActivity):
    BasePresenter<BackupViewMnemonicModel> {

    private var currentModel: BackupViewMnemonicModel? = null

    override fun bind(model: BackupViewMnemonicModel) {
        onOptionChange(model)
    }

    @SuppressLint("CommitTransaction")
    private fun onOptionChange(model: BackupViewMnemonicModel) {
        val transition = createTransition(currentModel, model)
        val fragment = when (model) {
            BackupViewMnemonicModel.BACKUP_DETAIL_PIN -> ViewPinCodeFragment()
            BackupViewMnemonicModel.BACKUP_DETAIL_RECOVERY_PHRASE -> ViewRecoveryPhraseFragment()
            BackupViewMnemonicModel.BACKUP_DETAIL_ERROR_BACKUP -> ViewErrorFragment(RestoreErrorOption.BACKUP_NOT_FOUND)
            BackupViewMnemonicModel.BACKUP_DETAIL_ERROR_PIN -> ViewErrorFragment(RestoreErrorOption.BACKUP_DECRYPTION_FAILED)
        }
        fragment.enterTransition = transition
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        currentModel = model
    }

    private fun createTransition(
        currentModel: BackupViewMnemonicModel?,
        model: BackupViewMnemonicModel
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