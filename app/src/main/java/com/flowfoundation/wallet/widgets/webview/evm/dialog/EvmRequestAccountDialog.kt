package com.flowfoundation.wallet.widgets.webview.evm.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogEvmRequestAccountBinding
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.widgets.webview.evm.fragment.EvmAccountFragment
import com.flowfoundation.wallet.manager.evm.DAppEVMConnectionManager
import com.flowfoundation.wallet.widgets.webview.evm.fragment.EvmSelectAccountFragment
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMDialogModel
import com.flowfoundation.wallet.widgets.webview.evm.viewmodel.EVMDialogViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class EvmRequestAccountDialog : BottomSheetDialogFragment() {
    private val TAG = EvmRequestAccountDialog::class.java.simpleName

    private var data: EVMDialogModel? = null
    private var result: Continuation<Boolean>? = null
    private lateinit var binding: DialogEvmRequestAccountBinding
    private lateinit var viewModel: EVMDialogViewModel

    private var evmAccountFragment: EvmAccountFragment? = null
    private var evmSelectAccountFragment: EvmSelectAccountFragment? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogEvmRequestAccountBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if we have the required data
        if (result == null || data == null) {
            logd(TAG, "Missing required data, dismissing dialog")
            return
        }

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[EVMDialogViewModel::class.java]

        // Set dialog data to ViewModel FIRST, before creating fragments
        data?.let {
            logd(TAG, "Setting dialog data: ${it.title}, URL: ${it.url}")
            viewModel.setDialogData(it)
        }

        // Ensure the Manager is loaded with the latest accounts
        DAppEVMConnectionManager.refreshAccounts()

        setupObservers()
        setupFragments()

        logd(TAG, "Dialog setup completed with current selected account: ${DAppEVMConnectionManager.getCurrentAccount()?.address}")
    }

    private fun setupFragments() {
        // Initialize fragments
        evmAccountFragment = EvmAccountFragment().apply {
            setCallbacks(
                onConnect = {
                    logd(TAG, "Connect callback triggered")
                    result?.resume(true)
                    dismiss()
                },
                onCancel = {
                    logd(TAG, "Cancel callback triggered")
                    result?.resume(false)
                    dismiss()
                }
            )
        }

        evmSelectAccountFragment = EvmSelectAccountFragment()

        // Load initial fragment
        childFragmentManager.beginTransaction()
            .replace(binding.flContainer.id, evmAccountFragment!!)
            .commit()

        logd(TAG, "Fragments initialized and main fragment loaded")
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.currentFragment.collect { fragmentType ->
                logd(TAG, "Fragment type changed to: $fragmentType")
                when (fragmentType) {
                    EVMDialogViewModel.FragmentType.ACCOUNT -> showAccountFragment()
                    EVMDialogViewModel.FragmentType.SELECT_ACCOUNT -> showSelectAccountFragment()
                }
            }
        }
    }

    private fun showAccountFragment() {
        logd(TAG, "Switching to account fragment")

        evmAccountFragment?.let { fragment ->
            logd(TAG, "Executing fragment transaction for account")
            childFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_left,
                    R.anim.slide_out_right,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
                .replace(binding.flContainer.id, fragment)
                .commitAllowingStateLoss()
            logd(TAG, "Fragment transaction committed for account")
        } ?: run {
            logd(TAG, "ERROR: evmAccountFragment is null!")
        }
    }

    private fun showSelectAccountFragment() {
        logd(TAG, "Switching to select account fragment")

        // Create a fresh instance to avoid fragment reuse issues
        val fragment = EvmSelectAccountFragment()

        logd(TAG, "Executing fragment transaction for select account")
        childFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            .replace(binding.flContainer.id, fragment)
            .commitAllowingStateLoss()
        logd(TAG, "Fragment transaction committed for select account")

        evmSelectAccountFragment = fragment
    }

    override fun onCancel(dialog: DialogInterface) {
        logd(TAG, "Dialog cancelled")
        result?.resume(false)
    }

    suspend fun show(
        fragmentManager: FragmentManager,
        data: EVMDialogModel,
    ) = suspendCoroutine { result ->
        logd(TAG, "Showing dialog for: ${data.title}")
        this.result = result
        this.data = data
        show(fragmentManager, "EvmRequestAccountDialog")
    }

    override fun onResume() {
        if (result == null) {
            logd(TAG, "No result continuation found, dismissing dialog")
            dismiss()
        }
        super.onResume()
    }
}
