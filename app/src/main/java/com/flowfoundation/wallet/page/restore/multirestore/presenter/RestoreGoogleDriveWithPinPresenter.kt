package com.flowfoundation.wallet.page.restore.multirestore.presenter

import androidx.transition.Fade
import androidx.transition.Transition
import com.google.android.material.transition.MaterialSharedAxis
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestoreGoogleDriveErrorFragment
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestoreGoogleDriveFragment
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestoreGoogleDriveWithPinFragment
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestorePinCodeFragment
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreErrorOption
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreGoogleDriveOption


class RestoreGoogleDriveWithPinPresenter(
    private val parentFragment: RestoreGoogleDriveWithPinFragment
) : BasePresenter<RestoreGoogleDriveOption> {

    private var currentOption: RestoreGoogleDriveOption? = null

    override fun bind(model: RestoreGoogleDriveOption) {
        onOptionChange(model)
    }

    private fun onOptionChange(option: RestoreGoogleDriveOption) {
        val transition = createTransition(option)
        val fragment = when (option) {
            RestoreGoogleDriveOption.RESTORE_PIN -> RestorePinCodeFragment()
            RestoreGoogleDriveOption.RESTORE_GOOGLE_DRIVE -> RestoreGoogleDriveFragment()
            RestoreGoogleDriveOption.RESTORE_ERROR_BACKUP -> RestoreGoogleDriveErrorFragment(RestoreErrorOption.BACKUP_NOT_FOUND)
            RestoreGoogleDriveOption.RESTORE_ERROR_PIN -> RestoreGoogleDriveErrorFragment(RestoreErrorOption.BACKUP_DECRYPTION_FAILED)
        }
        fragment.enterTransition = transition
        parentFragment.childFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        currentOption = option
    }

    private fun createTransition(
        option: RestoreGoogleDriveOption
    ): Transition {
        if (currentOption == null) {
            return Fade().apply { duration = 50 }
        }
        val transition = MaterialSharedAxis(MaterialSharedAxis.X, true)

        transition.addTarget(currentOption!!.layoutId)
        transition.addTarget(option.layoutId)
        return transition
    }
}