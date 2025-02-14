package com.flowfoundation.wallet.page.restore.multirestore.presenter

import androidx.transition.Fade
import androidx.transition.Transition
import com.google.android.material.transition.MaterialSharedAxis
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestoreDropboxErrorFragment
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestoreDropboxFragment
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestoreDropboxWithPinFragment
import com.flowfoundation.wallet.page.restore.multirestore.fragment.RestorePinCodeFragment
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreDropboxOption
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreErrorOption


class RestoreDropboxWithPinPresenter(
    private val parentFragment: RestoreDropboxWithPinFragment
) : BasePresenter<RestoreDropboxOption> {

    private var currentOption: RestoreDropboxOption? = null

    override fun bind(model: RestoreDropboxOption) {
        onOptionChange(model)
    }

    private fun onOptionChange(option: RestoreDropboxOption) {
        val transition = createTransition(option)
        val fragment = when (option) {
            RestoreDropboxOption.RESTORE_PIN -> RestorePinCodeFragment()
            RestoreDropboxOption.RESTORE_DROPBOX -> RestoreDropboxFragment()
            RestoreDropboxOption.RESTORE_ERROR_BACKUP -> RestoreDropboxErrorFragment(RestoreErrorOption.BACKUP_NOT_FOUND)
            RestoreDropboxOption.RESTORE_ERROR_PIN -> RestoreDropboxErrorFragment(RestoreErrorOption.BACKUP_DECRYPTION_FAILED)
        }
        fragment.enterTransition = transition
        parentFragment.childFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        currentOption = option
    }

    private fun createTransition(
        option: RestoreDropboxOption
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