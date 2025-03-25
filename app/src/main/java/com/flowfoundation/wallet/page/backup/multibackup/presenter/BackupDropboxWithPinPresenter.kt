package com.flowfoundation.wallet.page.backup.multibackup.presenter

import androidx.lifecycle.ViewModelProvider
import androidx.transition.Fade
import androidx.transition.Transition
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupDropboxFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupDropboxPinCodeFragment
import com.flowfoundation.wallet.page.backup.multibackup.fragment.BackupDropboxWithPinFragment
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupCompletedItem
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupDropboxOption
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.MultiBackupViewModel
import com.google.android.material.transition.MaterialSharedAxis


class BackupDropboxWithPinPresenter(private val parentFragment: BackupDropboxWithPinFragment) :
    BasePresenter<BackupDropboxOption> {

    private var currentOption: BackupDropboxOption? = null

    private val backupViewModel by lazy {
        ViewModelProvider(parentFragment.requireActivity())[MultiBackupViewModel::class.java]
    }

    override fun bind(model: BackupDropboxOption) {
        onOptionChange(model)
    }

    fun toNext(mnemonic: String) {
        backupViewModel.toNext(BackupCompletedItem(type = BackupType.DROPBOX, mnemonic = mnemonic))
    }

    private fun onOptionChange(option: BackupDropboxOption) {
        val transition = createTransition(option)
        val fragment = when (option) {
            BackupDropboxOption.BACKUP_PIN -> BackupDropboxPinCodeFragment()
            BackupDropboxOption.BACKUP_DROPBOX -> BackupDropboxFragment()
        }
        fragment.enterTransition = transition
        parentFragment.childFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        currentOption = option
    }

    private fun createTransition(
        option: BackupDropboxOption
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