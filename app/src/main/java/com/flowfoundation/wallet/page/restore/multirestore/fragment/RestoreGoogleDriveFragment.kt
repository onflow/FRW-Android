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
            override fun onReceive(context: Context, intent: Intent?) {
                val data = intent?.getParcelableArrayListExtra<BackupItem>(EXTRA_CONTENT) ?: return
                if (data.isEmpty()) {
                    onRestoreEmpty()
                } else {
                    if (data.size > 1) {
                        showAccountList(data)
                    } else {
                        val model = data.firstOrNull()
                        if (model == null) {
                            onRestoreEmpty()
                            return
                        }
                        restoreViewModel.addWalletInfo(model.userName, model.address)
                        restoreViewModel.toPinCode(model.data)
                    }
                }
            }
        }
    }

    private fun showAccountList(data: List<BackupItem>) {
        binding.btnNext.setVisible(false)
        with(binding.rvAccountList) {
            adapter = MultiAccountAdapter().apply {
                setNewDiffData(data)
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

private class MultiAccountAdapter : BaseAdapter<BackupItem>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return MultiAccountPresenter(parent.getItemView(R.layout.item_wallet_restore_username))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        (holder as MultiAccountPresenter).bind(item)
    }
}

private class MultiAccountPresenter(private val view: View) : BaseViewHolder(view),
    BasePresenter<BackupItem> {
    private val restoreViewModel by lazy {
        ViewModelProvider(findActivity(view) as FragmentActivity)[MultiRestoreViewModel::class.java]
    }

    override fun bind(model: BackupItem) {
        view.findViewById<TextView>(R.id.username).text = model.userName
        view.setOnClickListener {
            restoreViewModel.addWalletInfo(model.userName, model.address)
            restoreViewModel.toPinCode(model.data)
        }
    }
}
