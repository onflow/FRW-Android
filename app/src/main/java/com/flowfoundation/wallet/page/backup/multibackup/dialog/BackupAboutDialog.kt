package com.flowfoundation.wallet.page.backup.multibackup.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogBackupAboutBinding
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupAbout
import com.flowfoundation.wallet.utils.extensions.res2String
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class BackupAboutDialog: BottomSheetDialogFragment() {

    private var aboutType = BackupAbout.ABOUT_MULTI_BACKUP
    private lateinit var binding: DialogBackupAboutBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogBackupAboutBinding.inflate(inflater)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface: DialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            setupFullHeight(bottomSheetDialog)
        }
        return dialog
    }

    private fun setupFullHeight(bottomSheetDialog: BottomSheetDialog) {
        val bottomSheet =
            bottomSheetDialog.findViewById<ViewGroup>(R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            bottomSheet.requestLayout()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            tvAboutTitle.text = aboutType.titleId.res2String()
            tvAboutContent.text = aboutType.contentId.res2String()
            tvAboutNote.text = aboutType.noteId.res2String()
            btnOk.setOnClickListener {
                dismiss()
            }
            ivBack.setOnClickListener {
                dismiss()
            }
        }
    }

    fun show(
        fragmentManager: FragmentManager,
        aboutType: BackupAbout
    ) {
        this.aboutType = aboutType
        show(fragmentManager, "")
    }
}