package com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit.presenter

import android.widget.EditText
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityChildAccountEditBinding
import com.flowfoundation.wallet.manager.childaccount.ChildAccount
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit.ChildAccountEditActivity
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit.ChildAccountEditViewModel
import com.flowfoundation.wallet.page.profile.subpage.wallet.childaccountedit.model.ChildAccountEditModel
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.startGallery
import com.flowfoundation.wallet.widgets.ProgressDialog

class ChildAccountEditPresenter(
    private val binding: ActivityChildAccountEditBinding,
    private val activity: ChildAccountEditActivity,
) : BasePresenter<ChildAccountEditModel> {

    private var progressDialog: ProgressDialog? = null
    private val viewModel by lazy { ViewModelProvider(activity)[ChildAccountEditViewModel::class.java] }

    init {
        with(binding) {
            avatarContainer.setOnClickListener { startGallery(activity) }
            saveButton.setOnClickListener { viewModel.save(nameEditTextView.text(), descriptionEditTextView.text()) }
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