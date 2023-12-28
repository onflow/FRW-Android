package io.outblock.lilico.page.backup.multibackup.presenter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.databinding.FragmentBackupGoogleDriveBinding
import io.outblock.lilico.page.backup.multibackup.model.BackupGoogleDriveState
import io.outblock.lilico.page.backup.multibackup.viewmodel.BackupGoogleDriveViewModel
import io.outblock.lilico.page.backup.multibackup.viewmodel.MultiBackupViewModel
import io.outblock.lilico.utils.extensions.res2color
import io.outblock.lilico.utils.ioScope

class BackupGoogleDrivePresenter(
    private val fragment: Fragment,
    private val binding: FragmentBackupGoogleDriveBinding
) : BasePresenter<BackupGoogleDriveState> {

    private val viewModel by lazy {
        ViewModelProvider(fragment)[BackupGoogleDriveViewModel::class.java]
    }

    private val backupViewModel by lazy {
        ViewModelProvider(fragment.requireActivity())[MultiBackupViewModel::class.java]
    }

    private var currentState = BackupGoogleDriveState.CREATE_BACKUP

    init {
        with(binding) {
            clStatusLayout.visibility = View.GONE
            btnNext.setOnClickListener {
                when (currentState) {
                    BackupGoogleDriveState.CREATE_BACKUP -> viewModel.createBackup()
                    BackupGoogleDriveState.UPLOAD_BACKUP -> {
                        viewModel.uploadToChain()
                    }
                    BackupGoogleDriveState.UPLOAD_BACKUP_FAILURE -> {
                        viewModel.uploadToChain()
                    }
                    BackupGoogleDriveState.REGISTRATION_KEY_LIST -> viewModel.registrationKeyList()
                    BackupGoogleDriveState.NETWORK_ERROR -> viewModel.registrationKeyList()
                    BackupGoogleDriveState.BACKUP_SUCCESS -> backupViewModel.toNext()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun bind(model: BackupGoogleDriveState) {
        with(binding) {
            when (model) {
                BackupGoogleDriveState.CREATE_BACKUP -> {
                    tvOptionTitle.text = "Backup " + backupViewModel.getCurrentIndex() + ": " + "Google Drive Backup"
                    clStatusLayout.visibility = View.GONE
                    btnNext.text = "Create Backup"
                }
                BackupGoogleDriveState.UPLOAD_BACKUP -> {
                    tvOptionTitle.text = "Upload Backup"
                    clStatusLayout.visibility = View.VISIBLE
                    viewUpload.backgroundTintList = ColorStateList.valueOf(R.color.text_2.res2color())
                    tvUpload.setTextColor(R.color.text_2.res2color())
                    viewLine.setBackgroundColor(R.color.text_3.res2color())
                    viewRegistration.backgroundTintList = ColorStateList.valueOf(R.color.text_3.res2color())
                    tvRegistration.setTextColor(R.color.text_3.res2color())
                    btnNext.text = "Upload Backup"
                }
                BackupGoogleDriveState.UPLOAD_BACKUP_FAILURE -> {
                    tvOptionTitle.text = "Upload Backup"
                    clStatusLayout.visibility = View.VISIBLE
                    viewUpload.backgroundTintList = ColorStateList.valueOf(R.color.accent_red.res2color())
                    tvUpload.setTextColor(R.color.accent_red.res2color())
                    viewLine.setBackgroundColor(R.color.text_3.res2color())
                    viewRegistration.backgroundTintList = ColorStateList.valueOf(R.color.text_3.res2color())
                    tvRegistration.setTextColor(R.color.text_3.res2color())
                    btnNext.text = "Upload Again"
                }
                BackupGoogleDriveState.REGISTRATION_KEY_LIST -> {
                    tvOptionTitle.text = "Upload Backup"
                    clStatusLayout.visibility = View.VISIBLE
                    viewUpload.backgroundTintList = ColorStateList.valueOf(R.color.text_2.res2color())
                    tvUpload.setTextColor(R.color.text_2.res2color())
                    viewLine.setBackgroundColor(R.color.text_2.res2color())
                    viewRegistration.backgroundTintList = ColorStateList.valueOf(R.color.text_2.res2color())
                    tvRegistration.setTextColor(R.color.text_2.res2color())
                    btnNext.text = "Registration KeyList"
                }
                BackupGoogleDriveState.NETWORK_ERROR -> {
                    tvOptionTitle.text = "Network Connection Lost"
                    clStatusLayout.visibility = View.GONE
                    btnNext.text = "Try to Connect"
                }
                BackupGoogleDriveState.BACKUP_SUCCESS -> {
                    tvOptionTitle.text = "Backup Uploaded"
                    clStatusLayout.visibility = View.GONE
                    btnNext.text = "Next"
                }
            }
        }
    }
}