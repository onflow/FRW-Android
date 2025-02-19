package com.flowfoundation.wallet.page.profile.subpage.avatar

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.crowdin.platform.util.inflateWithCrowdin
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivityViewAvatarBinding
import com.flowfoundation.wallet.firebase.storage.uploadAvatarToFirebase
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.retrofit
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.loadAvatar
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.ProgressDialog
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import kotlinx.coroutines.delay

class ViewAvatarActivity : BaseActivity() {
    private val userInfo by lazy { intent.getParcelableExtra<UserInfoData>(EXTRA_USER_INFO)!! }

    private lateinit var binding: ActivityViewAvatarBinding
    private lateinit var photoPicker: ActivityResultLauncher<PickVisualMediaRequest>

    private val progressDialog by lazy { ProgressDialog(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewAvatarBinding.inflate(layoutInflater)
        UltimateBarX.with(this).fitWindow(false).light(false).applyStatusBar()
        UltimateBarX.with(this).fitWindow(false).light(false).applyNavigationBar()
        setContentView(binding.root)
        setupToolbar()

        binding.imageView.loadAvatar(userInfo.avatar)
        binding.doneButton.gone()
        binding.doneButton.setOnClickListener {
            with(binding.imageView) {
                val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return@with
                progressDialog.show()
                uploadAvatar(bitmap)
            }
        }
        photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                uiScope {
                    Glide.with(binding.imageView).load(uri)
                        .transform(CenterCrop(), CircleCrop())
                        .into(binding.imageView)
                    binding.doneButton.visible()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflateWithCrowdin(R.menu.edit_avatar, menu, resources)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.action_edit -> {
//                startGallery(this)
                startGallery()
            }
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun startGallery() {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.navigationIcon?.mutate()?.setTint(Color.WHITE)
        binding.toolbar.addStatusBarTopPadding()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    private fun uploadAvatar(bitmap: Bitmap) {
        try {
            ioScope {
                uploadAvatarToFirebase(bitmap) { avatarUrl ->
                    logd("upload avatar url", avatarUrl)
                    if (avatarUrl.isNullOrEmpty()) {
                        updateAvatarFailed()
                    }
                    val userInfo = AccountManager.userInfo()!!
                    val service = retrofit().create(ApiService::class.java)
                    ioScope {
                        val resp = service.updateProfile(mapOf("nickname" to userInfo.nickname, "avatar" to avatarUrl!!))
                        if (resp.status == 200) {
                            userInfo.avatar = avatarUrl
                            AccountManager.updateUserInfo(userInfo)
                            delay(200)
                            uiScope {
                                toast(msgRes = R.string.update_avatar_success)
                                progressDialog.dismiss()
                                finish()
                            }
                        } else {
                            updateAvatarFailed()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            updateAvatarFailed()
        }
    }

    private fun updateAvatarFailed() {
        uiScope {
            toast(msgRes = R.string.update_avatar_failed)
            progressDialog.dismiss()
            binding.doneButton.gone()
            binding.imageView.loadAvatar(userInfo.avatar)
        }
    }

    companion object {
        private const val EXTRA_USER_INFO = "EXTRA_USER_INFO"

        fun launch(context: Context, userInfo: UserInfoData) {
            context.startActivity(Intent(context, ViewAvatarActivity::class.java).apply {
                putExtra(EXTRA_USER_INFO, userInfo)
            })
        }
    }
}