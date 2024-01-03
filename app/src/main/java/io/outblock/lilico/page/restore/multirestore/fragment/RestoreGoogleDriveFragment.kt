package io.outblock.lilico.page.restore.multirestore.fragment

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
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.base.recyclerview.BaseAdapter
import io.outblock.lilico.base.recyclerview.BaseViewHolder
import io.outblock.lilico.base.recyclerview.getItemView
import io.outblock.lilico.databinding.FragmentRestoreGoogleDriveBinding
import io.outblock.lilico.manager.backup.ACTION_GOOGLE_DRIVE_RESTORE_FINISH
import io.outblock.lilico.manager.backup.BackupItem
import io.outblock.lilico.manager.backup.decryptMnemonic
import io.outblock.lilico.manager.drive.EXTRA_CONTENT
import io.outblock.lilico.manager.drive.GoogleDriveAuthActivity
import io.outblock.lilico.page.restore.multirestore.viewmodel.MultiRestoreViewModel
import io.outblock.lilico.utils.extensions.dp2px
import io.outblock.lilico.utils.extensions.setVisible
import io.outblock.lilico.utils.findActivity
import io.outblock.lilico.utils.toast
import io.outblock.lilico.widgets.itemdecoration.ColorDividerItemDecoration


class RestoreGoogleDriveFragment: Fragment() {
    private lateinit var binding: FragmentRestoreGoogleDriveBinding
    private val restoreViewModel by lazy {
        ViewModelProvider(requireActivity())[MultiRestoreViewModel::class.java]
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
                        val mnemonic = decryptMnemonic(model.data)
                        if (mnemonic.isEmpty()) {
                            onRestoreEmpty()
                            return
                        }
                        restoreViewModel.addMnemonicToTransaction(mnemonic)
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
        toast(msg = "No backup found")
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
            restoreViewModel.addMnemonicToTransaction(decryptMnemonic(model.data))
        }
    }
}
