package com.flowfoundation.wallet.page.backup.multibackup.fragment

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
import com.flowfoundation.wallet.databinding.FragmentBackupCompletedBinding
import com.flowfoundation.wallet.manager.backup.ACTION_GOOGLE_DRIVE_CHECK_FINISH
import com.flowfoundation.wallet.manager.backup.BackupCryptoProvider
import com.flowfoundation.wallet.manager.drive.EXTRA_SUCCESS
import com.flowfoundation.wallet.manager.drive.GoogleDriveAuthActivity
import com.flowfoundation.wallet.manager.flowjvm.lastBlockAccount
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.mixpanel.MixpanelBackupProvider
import com.flowfoundation.wallet.network.model.LocationInfo
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.backup.multibackup.dialog.BackupFailedDialog
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupCompletedItem
import com.flowfoundation.wallet.page.backup.multibackup.view.BackupCompletedItemView
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.MultiBackupViewModel
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.nftco.flow.sdk.FlowAddress
import wallet.core.jni.HDWallet


class BackupCompletedFragment : Fragment() {

    private lateinit var binding: FragmentBackupCompletedBinding
    private lateinit var backupViewModel: MultiBackupViewModel

    private var isGoogleDriveBackupSuccess: Boolean? = null
    private var isRecoveryPhraseBackupSuccess: Boolean? = null
    private var isGoogleDriveCheckLoading = false
    private var isRecoveryPhraseCheckLoading = false
    private var locationInfo: LocationInfo? = null

    private val checkFinishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isGoogleDriveBackupSuccess = intent?.getBooleanExtra(EXTRA_SUCCESS, false) ?: false
            backupViewModel.getCompletedList().firstOrNull { it.type == BackupType.GOOGLE_DRIVE }?.let {
                binding.llItemLayout.addView(BackupCompletedItemView(requireContext()).apply {
                    setItemInfo(it, locationInfo, isGoogleDriveBackupSuccess)
                }, 0)
            }
            isGoogleDriveCheckLoading = false
            checkLoadingStatus()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupCompletedBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LocalBroadcastManager.getInstance(Env.getApp()).registerReceiver(
            checkFinishReceiver, IntentFilter(
                ACTION_GOOGLE_DRIVE_CHECK_FINISH
            )
        )
        backupViewModel = ViewModelProvider(this.requireActivity())[MultiBackupViewModel::class.java].apply {
            locationInfoLiveData.observe(viewLifecycleOwner) { info ->
                setCompletedItemList(info)
            }
            getLocationInfo()
        }
        with(binding) {
            val optionList = backupViewModel.getBackupOptionList()
            if (optionList.size > 2) {
                ivOptionIconFirst.setImageResource(optionList[0].iconId)
                ivOptionIconSecond.setImageResource(optionList[1].iconId)
                ivOptionIconPlus.setVisible()
                ivOptionIconSecond.setVisible()
            } else {
                ivOptionIconFirst.setImageResource(optionList[0].iconId)
                ivOptionIconPlus.setVisible(false)
                ivOptionIconSecond.setVisible(false)
            }
            binding.lavLoading.visible()
            binding.btnNext.isEnabled = false

            btnNext.setOnClickListener {
                val provider = when {
                    isGoogleDriveBackupSuccess == null -> MixpanelBackupProvider.SEED_PHRASE
                    isRecoveryPhraseBackupSuccess == null -> MixpanelBackupProvider.GOOGLE_DRIVE
                    else -> null
                }

                val isSuccess = when (provider) {
                    MixpanelBackupProvider.SEED_PHRASE -> isRecoveryPhraseBackupSuccess
                    MixpanelBackupProvider.GOOGLE_DRIVE -> isGoogleDriveBackupSuccess
                    else -> isGoogleDriveBackupSuccess == true && isRecoveryPhraseBackupSuccess == true
                }

                if (isSuccess == true) {
                    requireActivity().finish()
                } else {
                    BackupFailedDialog(requireActivity()).show()
                }
            }
        }
    }

    private fun checkLoadingStatus() {
        if (isGoogleDriveCheckLoading || isRecoveryPhraseCheckLoading) {
            binding.lavLoading.visible()
            binding.btnNext.isEnabled = false
        } else {
            binding.lavLoading.gone()
            binding.btnNext.isEnabled = true
        }
    }

    private fun checkGoogleDriveBackup(mnemnoic: String) {
        isGoogleDriveCheckLoading = true
        isGoogleDriveBackupSuccess = false
        GoogleDriveAuthActivity.checkMultiBackup(requireContext(), mnemnoic)
    }

    private fun setCompletedItemList(locationInfo: LocationInfo?) {
        this.locationInfo = locationInfo
        with(binding) {
            tvOptionNote.gone()
            llItemLayout.removeAllViews()
            backupViewModel.getCompletedList().forEach {
                if (it.type == BackupType.GOOGLE_DRIVE) {
                    checkGoogleDriveBackup(it.mnemonic)
                } else {
                    checkRecoveryPhrase(it)
                }
            }
        }
    }

    private fun checkRecoveryPhrase(item: BackupCompletedItem) {
        isRecoveryPhraseCheckLoading = true
        isRecoveryPhraseBackupSuccess = false
        val backupProvider = BackupCryptoProvider(HDWallet(item.mnemonic, ""))

        val blockAccount = FlowAddress(WalletManager.wallet()?.walletAddress().orEmpty()).lastBlockAccount()
        isRecoveryPhraseBackupSuccess = blockAccount?.keys?.firstOrNull { backupProvider.getPublicKey() == it.publicKey.base16Value } != null
        binding.llItemLayout.addView(BackupCompletedItemView(requireContext()).apply {
            setItemInfo(item, locationInfo, isRecoveryPhraseBackupSuccess)
        })
        isRecoveryPhraseCheckLoading = false
        checkLoadingStatus()
    }

    override fun onDestroyView() {
        LocalBroadcastManager.getInstance(Env.getApp()).unregisterReceiver(checkFinishReceiver)
        super.onDestroyView()
    }
}