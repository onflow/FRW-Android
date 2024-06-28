package com.flowfoundation.wallet.page.wallet.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.flowfoundation.wallet.databinding.DialogChangeWallpaperBinding
import com.flowfoundation.wallet.page.profile.subpage.theme.WallpaperSettingActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class ChangeWallpaperDialog: BottomSheetDialogFragment() {

    private lateinit var binding: DialogChangeWallpaperBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogChangeWallpaperBinding.inflate(inflater)
        return binding.rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            tvCancel.setOnClickListener { dismissAllowingStateLoss() }
            btnChange.setOnClickListener {
                WallpaperSettingActivity.launch(view.context)
                dismissAllowingStateLoss()
            }
        }
    }

    companion object {

        fun show(fragmentManager: FragmentManager) {
            ChangeWallpaperDialog().showNow(fragmentManager, "")
        }
    }
}