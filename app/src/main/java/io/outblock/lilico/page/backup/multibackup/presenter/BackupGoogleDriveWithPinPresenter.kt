package io.outblock.lilico.page.backup.multibackup.presenter

import androidx.lifecycle.ViewModelProvider
import androidx.transition.Fade
import androidx.transition.Transition
import com.google.android.material.transition.MaterialSharedAxis
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.page.backup.multibackup.fragment.BackupGoogleDriveFragment
import io.outblock.lilico.page.backup.multibackup.fragment.BackupGoogleDriveWithPinFragment
import io.outblock.lilico.page.backup.multibackup.fragment.BackupPinCodeFragment
import io.outblock.lilico.page.backup.multibackup.model.BackupGoogleDriveOption
import io.outblock.lilico.page.backup.multibackup.viewmodel.MultiBackupViewModel


class BackupGoogleDriveWithPinPresenter(private val parentFragment: BackupGoogleDriveWithPinFragment) :
    BasePresenter<BackupGoogleDriveOption> {

    private var currentOption: BackupGoogleDriveOption? = null

    private val backupViewModel by lazy {
        ViewModelProvider(parentFragment.requireActivity())[MultiBackupViewModel::class.java]
    }

    override fun bind(model: BackupGoogleDriveOption) {
        onOptionChange(model)
    }

    fun toNext() {
        backupViewModel.toNext()
    }

    private fun onOptionChange(option: BackupGoogleDriveOption) {
        val transition = createTransition(option)
        val fragment = when (option) {
            BackupGoogleDriveOption.BACKUP_PIN -> BackupPinCodeFragment()
            BackupGoogleDriveOption.BACKUP_GOOGLE_DRIVE -> BackupGoogleDriveFragment()
        }
        fragment.enterTransition = transition
        parentFragment.childFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        currentOption = option
    }

    private fun createTransition(
        option: BackupGoogleDriveOption
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