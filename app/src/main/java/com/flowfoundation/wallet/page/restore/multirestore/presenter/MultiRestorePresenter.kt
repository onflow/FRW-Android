package com.flowfoundation.wallet.page.restore.multirestore.presenter

import android.annotation.SuppressLint
import androidx.transition.Fade
import androidx.transition.Transition
import com.google.android.material.transition.MaterialSharedAxis
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.page.restore.multirestore.MultiRestoreActivity
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestoreCompletedFragment
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestoreErrorFragment
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestoreGoogleDriveWithPinFragment
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestoreRecoveryPhraseFragment
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestoreStartFragment
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreErrorOption
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreOption
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreOptionModel


class MultiRestorePresenter(private val activity: MultiRestoreActivity): BasePresenter<RestoreOptionModel> {

    private var currentModel: RestoreOptionModel? = null

    override fun bind(model: RestoreOptionModel) {
        onOptionChange(model)
    }

    @SuppressLint("CommitTransaction")
    private fun onOptionChange(model: RestoreOptionModel) {
        val transition = createTransition(currentModel, model)
        val fragment = when (model.option) {
            RestoreOption.RESTORE_START -> RestoreStartFragment()
            RestoreOption.RESTORE_FROM_GOOGLE_DRIVE -> RestoreGoogleDriveWithPinFragment()
            RestoreOption.RESTORE_FROM_RECOVERY_PHRASE -> RestoreRecoveryPhraseFragment()
            RestoreOption.RESTORE_COMPLETED -> RestoreCompletedFragment()
            RestoreOption.RESTORE_FAILED -> RestoreErrorFragment(RestoreErrorOption.RESTORE_FAILED)
            RestoreOption.RESTORE_FAILED_NO_ACCOUNT -> RestoreErrorFragment(RestoreErrorOption.NO_ACCOUNT_FOUND)
        }
        fragment.enterTransition = transition
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        currentModel = model
    }

    private fun createTransition(
        currentModel: RestoreOptionModel?,
        model: RestoreOptionModel
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