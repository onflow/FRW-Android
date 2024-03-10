package io.outblock.lilico.page.restore.multirestore.presenter

import androidx.transition.Fade
import androidx.transition.Transition
import com.google.android.material.transition.MaterialSharedAxis
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.page.restore.multirestore.fragment.RestoreGoogleDriveFragment
import io.outblock.lilico.page.restore.multirestore.fragment.RestoreGoogleDriveWithPinFragment
import io.outblock.lilico.page.restore.multirestore.fragment.RestorePinCodeFragment
import io.outblock.lilico.page.restore.multirestore.model.RestoreGoogleDriveOption


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