package com.flowfoundation.wallet.page.dialog.accounts

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogAccountSwitchBinding
import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.manager.app.isTestnet
import com.flowfoundation.wallet.page.dialog.accounts.adapter.AccountListAdapter
import com.flowfoundation.wallet.page.restore.WalletRestoreActivity
import com.flowfoundation.wallet.page.walletcreate.WALLET_CREATE_STEP_USERNAME
import com.flowfoundation.wallet.page.walletcreate.WalletCreateActivity
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.DialogType
import com.flowfoundation.wallet.widgets.SwitchNetworkDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AccountSwitchDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogAccountSwitchBinding

    private val adapter by lazy { AccountListAdapter() }

    private var isFullScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logd("AccountSwitchDialog", "onCreate() called")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        logd("AccountSwitchDialog", "onCreateView() called")
        binding = DialogAccountSwitchBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logd("AccountSwitchDialog", "onViewCreated() called")
        binding.root.requestFocus()

        binding.tvImportAccount.setOnClickListener {
            logd("AccountSwitchDialog", "Import account clicked")
            WalletRestoreActivity.launch(requireContext())
            dismiss()
        }
        binding.tvNewAccount.setOnClickListener {
            logd("AccountSwitchDialog", "New account clicked")
            if (isTestnet()) {
                SwitchNetworkDialog(requireContext(), DialogType.CREATE).show()
            } else {
                WalletCreateActivity.launch(requireContext(), step = WALLET_CREATE_STEP_USERNAME)
                dismiss()
            }
        }
        binding.tvViewMore.setOnClickListener {
            logd("AccountSwitchDialog", "View more clicked")
            (dialog as BottomSheetDialog?)?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { dialog ->
                val behavior = BottomSheetBehavior.from(dialog)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                val displayMetrics = DisplayMetrics()
                activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
                val screenHeight = displayMetrics.heightPixels

                dialog.layoutParams.height = screenHeight
                dialog.requestLayout()
                isFullScreen = true
                it.gone()
            }
        }

        with(binding.recyclerView) {
            adapter = this@AccountSwitchDialog.adapter
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            addOnScrollListener(object : OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val firstVisiblePosition = (layoutManager as LinearLayoutManager?)?.findFirstCompletelyVisibleItemPosition() ?:0
                    showViewMore(firstVisiblePosition == 0)
                }
            })
        }

        ioScope {
            logd("AccountSwitchDialog", "Starting to fetch account list")
            val list = AccountManager.getSwitchAccountList()
            logd("AccountSwitchDialog", "Got account list: $list")
            uiScope {
                adapter.setNewDiffData(list)
                logd("AccountSwitchDialog", "Set data to adapter, item count: ${adapter.itemCount}")
                initDialogHeight()
                showViewMore(true)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        logd("AccountSwitchDialog", "onStart() called")
    }

    override fun onResume() {
        super.onResume()
        logd("AccountSwitchDialog", "onResume() called")
    }

    private fun initDialogHeight() {
        (dialog as BottomSheetDialog?)?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { dialog ->
            if (adapter.itemCount < 3) {
                dialog.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.recyclerView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            } else {
                dialog.layoutParams.height = 420.dp2px().toInt()
            }
        }
    }

    private fun showViewMore(isFirstItemVisible: Boolean) {
        if (isFullScreen) {
            return
        }
        (binding.recyclerView.layoutManager as LinearLayoutManager?)?.run {
            binding.tvViewMore.setVisible(childCount < itemCount && isFirstItemVisible)
        }
    }

    companion object {
        fun show(fragmentManager: FragmentManager) {
            logd("AccountSwitchDialog", "show() called")
            AccountSwitchDialog().showNow(fragmentManager, "")
        }
    }
}