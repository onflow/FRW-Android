package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit.presenter

import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityChildAccountEditBinding
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit.ChildAccountEditActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit.ChildAccountEditViewModel
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit.model.ChildAccountEditModel
import com.flowfoundation.wallet.utils.CACHE_VIDEO_PATH
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.toFile
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.ProgressDialog
import java.io.File

class ChildAccountEditPresenter(
    private val binding: ActivityChildAccountEditBinding,
    private val activity: ChildAccountEditActivity,
) : BasePresenter<ChildAccountEditModel> {

    private var progressDialog: ProgressDialog? = null
    private val viewModel by lazy { ViewModelProvider(activity)[ChildAccountEditViewModel::class.java] }
    private  lateinit var photoPicker: ActivityResultLauncher<PickVisualMediaRequest>

    init {
        with(binding) {
            avatarContainer.setOnClickListener {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            saveButton.setOnClickListener { viewModel.save(nameEditTextView.text(), descriptionEditTextView.text()) }
        }
        photoPicker = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            ioScope {
                val file = uri?.toFile(File(CACHE_VIDEO_PATH, "${System.currentTimeMillis()}" + ".jpg").absolutePath) ?: return@ioScope
                uiScope {
                    bind(ChildAccountEditModel(avatarFile = file))
                    viewModel.updateAvatar(file.absolutePath)
                }
            }
        }
    }

    override fun bind(model: ChildAccountEditModel) {
        model.account?.let { updateAccount(it) }
        model.avatarFile?.let { Glide.with(binding.avatarView).load(it).into(binding.avatarView) }
        model.showProgressDialog?.let { toggleProgressDialog(it) }
    }

    private fun updateAccount(account: ChildAccount) {
        with(binding) {
            avatarView.loadAvatar(account.icon)
            nameEditTextView.setText(account.name)
            descriptionEditTextView.setText(account.description)
        }
    }

    private fun EditText.text() = text?.toString().orEmpty()

    private fun toggleProgressDialog(isVisible: Boolean) {
        if (isVisible) {
            progressDialog = ProgressDialog(activity, cancelable = false)
            progressDialog?.show()
        } else {
            progressDialog?.dismiss()
        }
    }
}