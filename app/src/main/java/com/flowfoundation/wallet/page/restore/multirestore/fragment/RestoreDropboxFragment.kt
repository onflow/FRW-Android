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
import com.flowfoundation.wallet.databinding.FragmentRestoreDropboxBinding
import com.flowfoundation.wallet.manager.backup.BackupItem
import com.flowfoundation.wallet.manager.dropbox.EXTRA_CONTENT
import com.flowfoundation.wallet.manager.dropbox.ACTION_DROPBOX_RESTORE_FINISH
import com.flowfoundation.wallet.manager.dropbox.DropboxAuthActivity
import com.flowfoundation.wallet.page.restore.multirestore.viewmodel.MultiRestoreViewModel
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.logw
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class RestoreDropboxFragment: Fragment() {
    private lateinit var binding: FragmentRestoreDropboxBinding
    private val restoreViewModel by lazy {
        ViewModelProvider(requireParentFragment().requireActivity())[MultiRestoreViewModel::class.java]
    }

    private val dropboxRestoreReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                logw("Dropbox", "broadcast receive")
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
                        restoreViewModel.toDropboxPinCode(model.data)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        logw("Dropbox", "fragment onResume")

    }

    override fun onPause() {
        super.onPause()
        logw("Dropbox", "fragment onPause")
    }

    private fun showAccountList(data: List<BackupItem>) {
        binding.btnNext.setVisible(false)
        with(binding.rvAccountList) {
            adapter = DropboxMultiAccountAdapter().apply {
                setNewDiffData(data)
            }
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(
                ColorDividerItemDecoration(
                    Color.TRANSPARENT,
                    12.dp2px().toInt(),
                    LinearLayout.VERTICAL
                )
            )
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
        binding = FragmentRestoreDropboxBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(dropboxRestoreReceiver,
                IntentFilter(ACTION_DROPBOX_RESTORE_FINISH)
            )
        with(binding) {
            rvAccountList.setVisible(false)

            btnNext.setOnClickListener {
                if (btnNext.isProgressVisible()) {
                    return@setOnClickListener
                }
                btnNext.setProgressVisible(true)
                DropboxAuthActivity.multiRestoreMnemonicWithSignOut(requireContext())
            }
        }
    }

    override fun onDestroyView() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(dropboxRestoreReceiver)
        super.onDestroyView()
    }
}

private class DropboxMultiAccountAdapter : BaseAdapter<BackupItem>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return DropboxMultiAccountPresenter(parent.getItemView(R.layout.item_wallet_restore_username))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        (holder as DropboxMultiAccountPresenter).bind(item)
    }
}

private class DropboxMultiAccountPresenter(private val view: View) : BaseViewHolder(view),
    BasePresenter<BackupItem> {
    private val restoreViewModel by lazy {
        ViewModelProvider(findActivity(view) as FragmentActivity)[MultiRestoreViewModel::class.java]
    }

    override fun bind(model: BackupItem) {
        view.findViewById<TextView>(R.id.username).text = model.userName
        view.setOnClickListener {
            restoreViewModel.addWalletInfo(model.userName, model.address)
            restoreViewModel.toDropboxPinCode(model.data)
        }
    }
}