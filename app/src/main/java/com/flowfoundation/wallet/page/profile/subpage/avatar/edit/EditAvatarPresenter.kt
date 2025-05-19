package com.flowfoundation.wallet.page.profile.subpage.avatar.edit

import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivityEditAvatarBinding
import com.flowfoundation.wallet.network.model.Nft
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.nft.nftlist.cover
import com.flowfoundation.wallet.page.nft.nftlist.name
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.widgets.ProgressDialog
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class EditAvatarPresenter(
    private val activity: EditAvatarActivity,
    private val binding: ActivityEditAvatarBinding,
    userInfo: UserInfoData,
) : BasePresenter<EditAvatarModel> {

    private val avatarAdapter by lazy { AvatarListAdapter() }

    private val viewModel by lazy { ViewModelProvider(activity)[EditAvatarViewModel::class.java] }

    private val progressDialog by lazy { ProgressDialog(activity) }

    init {
        setupToolbar()
        binding.imageView.loadAvatar(userInfo.avatar)

        with(binding.avatarList) {
            adapter = avatarAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 2.dp2px().toInt()))
        }

        binding.doneButton.setOnClickListener { uploadAvatar() }
    }

    private fun uploadAvatar() {
        with(binding.imageView) {
            isDrawingCacheEnabled = true
            buildDrawingCache()
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return@with
            progressDialog.show()
            viewModel.uploadAvatar(bitmap)
        }
    }

    override fun bind(model: EditAvatarModel) {
        model.avatarList?.let { avatarAdapter.setNewDiffData(it) }
        model.selectedAvatar?.let { updateAvatar(it) }
        model.uploadResult?.let { onUploadResult(it) }
    }

    private fun onUploadResult(isSuccess: Boolean) {
        if (isSuccess) {
            progressDialog.dismiss()
            activity.finish()
        } else {
            progressDialog.dismiss()
            Toast.makeText(activity, "Upload fail", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAvatar(avatar: Any) {
        val url = if (avatar is Nft) avatar.cover().orEmpty() else avatar.toString()
        logd("avatar url", url)
        binding.imageView.loadAvatar(url, false)
        binding.nftNameView.text = if (avatar is Nft) avatar.name() else null
    }

    private fun setupToolbar() {
        with(activity) {
            setSupportActionBar(binding.toolbar)
            binding.toolbar.navigationIcon?.mutate()?.setTint(Color.WHITE)
            binding.toolbar.addStatusBarTopPadding()
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
        }
    }
}