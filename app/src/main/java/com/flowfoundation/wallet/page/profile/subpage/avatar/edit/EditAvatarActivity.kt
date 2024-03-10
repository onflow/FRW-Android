package com.flowfoundation.wallet.page.profile.subpage.avatar.edit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.zackratos.ultimatebarx.ultimatebarx.UltimateBarX
import com.flowfoundation.wallet.databinding.ActivityEditAvatarBinding
import com.flowfoundation.wallet.network.model.UserInfoData

class EditAvatarActivity : BaseActivity() {
    private val userInfo by lazy { intent.getParcelableExtra<UserInfoData>(EXTRA_USER_INFO)!! }

    private lateinit var binding: ActivityEditAvatarBinding
    private lateinit var presenter: EditAvatarPresenter
    private lateinit var viewModel: EditAvatarViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditAvatarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UltimateBarX.with(this).fitWindow(false).light(false).applyStatusBar()

        presenter = EditAvatarPresenter(this, binding, userInfo)
        viewModel = ViewModelProvider(this)[EditAvatarViewModel::class.java].apply {
            avatarListLiveData.observe(this@EditAvatarActivity) { presenter.bind(EditAvatarModel(avatarList = it)) }
            selectedAvatarLiveData.observe(this@EditAvatarActivity) { presenter.bind(EditAvatarModel(selectedAvatar = it)) }
            uploadResultLiveData.observe(this@EditAvatarActivity) { presenter.bind(EditAvatarModel(uploadResult = it)) }
            loadNft(userInfo)
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    companion object {
        private const val EXTRA_USER_INFO = "EXTRA_USER_INFO"

        fun launch(context: Context, userInfo: UserInfoData) {
            context.startActivity(Intent(context, EditAvatarActivity::class.java).apply {
                putExtra(EXTRA_USER_INFO, userInfo)
            })
        }
    }
}