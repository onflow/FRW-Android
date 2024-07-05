package com.flowfoundation.wallet.page.walletrestore.fragments.guide

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentWalletRestoreGuideBinding
import com.flowfoundation.wallet.manager.drive.ACTION_GOOGLE_DRIVE_RESTORE_FINISH
import com.flowfoundation.wallet.manager.drive.DriveItem
import com.flowfoundation.wallet.manager.drive.EXTRA_CONTENT
import com.flowfoundation.wallet.manager.drive.GoogleDriveAuthActivity
import com.flowfoundation.wallet.page.walletrestore.WALLET_RESTORE_STEP_DRIVE_PASSWORD
import com.flowfoundation.wallet.page.walletrestore.WALLET_RESTORE_STEP_DRIVE_USERNAME
import com.flowfoundation.wallet.page.walletrestore.WALLET_RESTORE_STEP_MNEMONIC
import com.flowfoundation.wallet.page.walletrestore.WalletRestoreViewModel
import com.flowfoundation.wallet.utils.toast

class WalletRestoreGuideFragment : Fragment() {

    private lateinit var binding: FragmentWalletRestoreGuideBinding

    private val pageViewModel by lazy { ViewModelProvider(requireActivity())[WalletRestoreViewModel::class.java] }

    private val googleDriveRestoreReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val data = intent?.getParcelableArrayListExtra<DriveItem>(EXTRA_CONTENT) ?: return
                if (data.isEmpty()) {
                    onRestoreEmpty()
                } else {
                    pageViewModel.changeStep(if (data.size > 1) WALLET_RESTORE_STEP_DRIVE_USERNAME else WALLET_RESTORE_STEP_DRIVE_PASSWORD, data)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWalletRestoreGuideBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(googleDriveRestoreReceiver, IntentFilter(ACTION_GOOGLE_DRIVE_RESTORE_FINISH))

        with(binding) {
            driveRestore.setOnClickListener {
                if (driveRestore.isProgressVisible()) {
                    return@setOnClickListener
                }
                driveRestore.setProgressVisible(true)
                GoogleDriveAuthActivity.restoreMnemonicWithSignOut(requireContext())
            }
            mnemonicRestore.setOnClickListener { pageViewModel.changeStep(WALLET_RESTORE_STEP_MNEMONIC) }
        }
    }

    override fun onDestroyView() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(googleDriveRestoreReceiver)
        super.onDestroyView()
    }

    private fun onRestoreEmpty() {
        toast(msgRes = R.string.no_backup_found)
        with(binding.driveRestore) {
            setProgressVisible(false)
        }
    }
}