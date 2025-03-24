package com.flowfoundation.wallet.page.restore.multirestore.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.base.recyclerview.getItemView
import com.flowfoundation.wallet.databinding.FragmentRestoreGoogleDriveBinding
import com.flowfoundation.wallet.manager.backup.ACTION_GOOGLE_DRIVE_RESTORE_FINISH
import com.flowfoundation.wallet.manager.backup.BackupItem
import com.flowfoundation.wallet.manager.drive.DriveItem
import com.flowfoundation.wallet.manager.drive.EXTRA_CONTENT
import com.flowfoundation.wallet.manager.drive.GoogleDriveAuthActivity
import com.flowfoundation.wallet.page.restore.multirestore.viewmodel.MultiRestoreViewModel
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration


class RestoreGoogleDriveFragment: Fragment() {
    private lateinit var binding: FragmentRestoreGoogleDriveBinding
    private val restoreViewModel by lazy {
        ViewModelProvider(requireParentFragment().requireActivity())[MultiRestoreViewModel::class.java]
    }

    private val googleDriveRestoreReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val driveItems = intent?.getParcelableArrayListExtra<DriveItem>(EXTRA_CONTENT) ?: return
                if (driveItems.isEmpty()) {
                    onRestoreEmpty()
                } else {
                    if (driveItems.size > 1) {
                        // Store the DriveItems in the ViewModel for later use
                        restoreViewModel.setDriveItems(driveItems)
                        // Show the list of usernames for selection
                        showAccountList(driveItems.map { it.username })
                    } else {
                        val model = driveItems.firstOrNull()
                        if (model == null) {
                            onRestoreEmpty()
                            return
                        }
                        // Store the DriveItem in the ViewModel for later use
                        restoreViewModel.setDriveItems(listOf(model))
                        // Proceed to password entry
                        restoreViewModel.toPinCode(model.data)
                    }
                }
            }
        }
    }

    private fun showAccountList(usernames: List<String>) {
        binding.btnNext.setVisible(false)
        with(binding.rvAccountList) {
            adapter = MultiAccountAdapter().apply {
                setNewDiffData(usernames)
            }
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 12.dp2px().toInt(), LinearLayout.VERTICAL))
            setVisible()
        }
    }

    private fun onRestoreEmpty() {
        restoreViewModel.toBackupNotFound()
        binding.btnNext.setProgressVisible(false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestoreGoogleDriveBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(googleDriveRestoreReceiver, IntentFilter(ACTION_GOOGLE_DRIVE_RESTORE_FINISH))
        with(binding) {
            rvAccountList.setVisible(false)

            btnNext.setOnClickListener {
                if (btnNext.isProgressVisible()) {
                    return@setOnClickListener
                }
                btnNext.setProgressVisible(true)
                GoogleDriveAuthActivity.multiRestoreMnemonicWithSignOut(requireContext())
            }
        }
    }

    override fun onDestroyView() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(googleDriveRestoreReceiver)
        super.onDestroyView()
    }
}

private class MultiAccountAdapter : BaseAdapter<String>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return MultiAccountPresenter(parent.getItemView(R.layout.item_wallet_restore_username))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        (holder as MultiAccountPresenter).bind(item)
    }
}

private class MultiAccountPresenter(private val view: View) : BaseViewHolder(view),
    BasePresenter<String> {
    private val restoreViewModel by lazy {
        ViewModelProvider(findActivity(view) as FragmentActivity)[MultiRestoreViewModel::class.java]
    }

    override fun bind(model: String) {
        view.findViewById<TextView>(R.id.username).text = model
        view.setOnClickListener {
            // Find the corresponding DriveItem and proceed to password entry
            restoreViewModel.getDriveItems().firstOrNull { it.username == model }?.let { driveItem ->
                restoreViewModel.toPinCode(driveItem.data)
            }
        }
    }
}
