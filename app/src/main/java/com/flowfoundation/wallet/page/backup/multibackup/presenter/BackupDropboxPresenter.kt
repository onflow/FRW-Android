package com.flowfoundation.wallet.page.backup.multibackup.presenter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentBackupDropboxBinding
import com.flowfoundation.wallet.databinding.FragmentBackupGoogleDriveBinding
import com.flowfoundation.wallet.manager.drive.GoogleDriveAuthActivity
import com.flowfoundation.wallet.page.backup.BackupListManager
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupCompletedItem
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupDropboxState
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupGoogleDriveState
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupOption
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupDropboxViewModel
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupGoogleDriveViewModel
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupGoogleDriveWithPinViewModel
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.MultiBackupViewModel
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.getPinCode

class BackupDropboxPresenter(
    private val fragment: Fragment,
    private val binding: FragmentBackupDropboxBinding
) : BasePresenter<BackupDropboxState> {

    private val viewModel by lazy {
        ViewModelProvider(fragment)[BackupDropboxViewModel::class.java]
    }

    private val backupViewModel by lazy {
        ViewModelProvider(fragment.requireActivity())[MultiBackupViewModel::class.java]
    }

    private var currentState = BackupDropboxState.CREATE_BACKUP

    init {
        with(binding) {
            //todo
            if (BackupListManager.hadBackupOption(BackupType.MANUAL)) {
                backupProgress.setProgressInfo(BackupOption.BACKUP_WITH_GOOGLE_DRIVE,
                    isCompleted = false,
                    isOnlyGoogleDrive = true
                )
            } else {
                backupProgress.setProgressInfo(BackupOption.BACKUP_WITH_GOOGLE_DRIVE, false)
            }
            clStatusLayout.visibility = View.GONE
            btnNext.setOnClickListener {
                if (btnNext.isProgressVisible()) {
                    return@setOnClickListener
                }
                btnNext.setProgressVisible(true)
                when (currentState) {
                    BackupDropboxState.CREATE_BACKUP -> loginDropbox()
                    BackupDropboxState.UPLOAD_BACKUP -> {
                        viewModel.uploadToChain()
                    }
                    BackupDropboxState.UPLOAD_BACKUP_FAILURE -> {
                        viewModel.uploadToChain()
                    }
                    BackupDropboxState.REGISTRATION_KEY_LIST -> {}
                    BackupDropboxState.NETWORK_ERROR -> viewModel.registrationKeyList()
                    BackupDropboxState.BACKUP_SUCCESS -> backupViewModel.toNext(BackupCompletedItem(type = BackupType.DROPBOX, mnemonic = viewModel.getMnemonic()))
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun bind(model: BackupDropboxState) {
        currentState = model
        with(binding) {
            when (model) {
                BackupDropboxState.CREATE_BACKUP -> {
                    btnNext.setProgressVisible(false)
                    tvOptionTitle.text = R.string.backup_step_dropbox.res2String()
                    clStatusLayout.visibility = View.GONE
                    btnNext.text = R.string.create_backup.res2String()
                }
                BackupDropboxState.UPLOAD_BACKUP -> {
                    btnNext.setProgressVisible(false)
                    tvOptionTitle.text = R.string.upload_backup.res2String()
                    clStatusLayout.visibility = View.VISIBLE
                    viewUpload.backgroundTintList = ColorStateList.valueOf(R.color.text_2.res2color())
                    tvUpload.setTextColor(R.color.text_2.res2color())
                    viewLine.setBackgroundColor(R.color.text_3.res2color())
                    viewRegistration.backgroundTintList = ColorStateList.valueOf(R.color.text_3.res2color())
                    tvRegistration.setTextColor(R.color.text_3.res2color())
                    btnNext.text = R.string.upload_backup.res2String()
                }
                BackupDropboxState.UPLOAD_BACKUP_FAILURE -> {
                    btnNext.setProgressVisible(false)
                    tvOptionTitle.text = R.string.upload_backup.res2String()
                    clStatusLayout.visibility = View.VISIBLE
                    viewUpload.backgroundTintList = ColorStateList.valueOf(R.color.accent_red.res2color())
                    tvUpload.setTextColor(R.color.accent_red.res2color())
                    viewLine.setBackgroundColor(R.color.text_3.res2color())
                    viewRegistration.backgroundTintList = ColorStateList.valueOf(R.color.text_3.res2color())
                    tvRegistration.setTextColor(R.color.text_3.res2color())
                    btnNext.text = R.string.upload_again.res2String()
                }
                BackupDropboxState.REGISTRATION_KEY_LIST -> {
                    tvOptionTitle.text = R.string.upload_backup.res2String()
                    clStatusLayout.visibility = View.VISIBLE
                    viewUpload.backgroundTintList = ColorStateList.valueOf(R.color.text_2.res2color())
                    tvUpload.setTextColor(R.color.text_2.res2color())
                    viewLine.setBackgroundColor(R.color.text_2.res2color())
                    viewRegistration.backgroundTintList = ColorStateList.valueOf(R.color.text_2.res2color())
                    tvRegistration.setTextColor(R.color.text_2.res2color())
                    btnNext.text = R.string.upload_backup.res2String()
                }
                BackupDropboxState.NETWORK_ERROR -> {
                    btnNext.setProgressVisible(false)
                    tvOptionTitle.text = R.string.network_connect_lost.res2String()
                    clStatusLayout.visibility = View.GONE
                    btnNext.text = R.string.try_to_connect.res2String()
                }
                BackupDropboxState.BACKUP_SUCCESS -> {
                    if (BackupListManager.hadBackupOption(BackupType.MANUAL)) {
                        backupProgress.setProgressInfo(BackupOption.BACKUP_WITH_GOOGLE_DRIVE,
                            isCompleted = true,
                            isOnlyGoogleDrive = true
                        )
                    }
                    btnNext.setProgressVisible(false)
                    tvOptionTitle.text = R.string.backup_uploaded.res2String()
                    clStatusLayout.visibility = View.VISIBLE
                    btnNext.text = R.string.next.res2String()
                }
            }
        }
    }

    private fun loginDropbox() {

    }

    fun uploadMnemonic(mnemonic: String) {
        GoogleDriveAuthActivity.multiBackupMnemonic(fragment.requireContext(), mnemonic)
    }
}