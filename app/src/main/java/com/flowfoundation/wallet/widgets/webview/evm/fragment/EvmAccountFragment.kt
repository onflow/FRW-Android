package com.flowfoundation.wallet.widgets.webview.evm.fragment

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentEvmAccountBinding
import com.flowfoundation.wallet.databinding.ItemSelectedEvmAccountBinding
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.blocklist.BlockManager
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.DAppEVMAccount
import com.flowfoundation.wallet.manager.evm.DAppEVMAccountType
import com.flowfoundation.wallet.page.browser.loadFavicon
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.utils.extensions.capitalizeV2
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.urlHost
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.shortenEVMString
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMDialogModel
import com.flowfoundation.wallet.widgets.webview.evm.viewmodel.EVMDialogViewModel
import kotlinx.coroutines.launch

class EvmAccountFragment : Fragment() {
    private val TAG = EvmAccountFragment::class.java.simpleName

    private var _binding: FragmentEvmAccountBinding? = null
    private val binding get() = _binding!!

    private var _selectedAccountBinding: ItemSelectedEvmAccountBinding? = null

    private val viewModel: EVMDialogViewModel by viewModels({ requireParentFragment() })

    private var onConnectCallback: (() -> Unit)? = null
    private var onCancelCallback: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEvmAccountBinding.inflate(inflater, container, false)

        // Create binding for the included selected account layout
        _selectedAccountBinding = ItemSelectedEvmAccountBinding.bind(binding.flSelectedAccount.getChildAt(0))

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupClickListeners()
        
        // Check for existing data in ViewModel (in case data was set before fragment creation)
        viewModel.dialogData.value?.let { data ->
            logd(TAG, "Found existing dialog data on view creation: ${data.title}")
            setupDialogContent(data)
        }
        
        viewModel.selectedAccount.value?.let { account ->
            logd(TAG, "Found existing selected account on view creation: ${account.address}")
            setupSelectedAccount(account)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.dialogData.collect { data ->
                logd(TAG, "Dialog data received: ${data?.title}, URL: ${data?.url}")
                data?.let { setupDialogContent(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedAccount.collect { account ->
                logd(TAG, "Selected account received: ${account?.address} (${account?.type})")
                account?.let { setupSelectedAccount(it) }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener {
            logd(TAG, "Cancel button clicked")
            onCancelCallback?.invoke()
        }

        binding.btnConnect.setOnClickListener {
            logd(TAG, "Connect button clicked")
            onConnectCallback?.invoke()
        }

        binding.flSelectedAccount.setOnClickListener {
            logd(TAG, "Selected account clicked, showing account selection")
            viewModel.showSelectAccount()
        }
    }

    private fun setupDialogContent(data: EVMDialogModel) {
        logd(TAG, "Setting up dialog content for: ${data.title}")

        with(binding) {
            // Load app icon and info
            ivIcon.loadFavicon(data.logo ?: data.url?.toFavIcon())
            tvName.text = data.title
            tvUrl.text = getString(R.string.requesting_access, data.url?.urlHost() ?: "")

            // Set network info
            tvNetwork.text = getString(R.string.connecting_on, chainNetWorkString().capitalizeV2())

            // Check for blocked URL
            ioScope {
                if (!data.url.isNullOrEmpty()) {
                    val isBlockedUrl = BlockManager.isBlocked(data.url)
                    uiScope {
                        flBlockedTip.setVisible(isBlockedUrl)
                        btnConnect.setVisible(!isBlockedUrl)
                        flBlockedConnect.setVisible(isBlockedUrl)

                        if (isBlockedUrl) {
                            flBlockedConnect.setOnClickListener {
                                logd(TAG, "Blocked connect button clicked")
                                onConnectCallback?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupSelectedAccount(account: DAppEVMAccount) {
        logd(TAG, "Setting up selected account: ${account.address}")

        val selectedBinding = _selectedAccountBinding ?: return

        // Setup account icon and emoji
        val emoji = AccountEmojiManager.getEmojiByAddress(account.address)

        with(selectedBinding) {
            // Setup emoji icon
            tvAccountIcon.text = Emoji.getEmojiById(emoji.emojiId)
            tvAccountIcon.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(emoji.emojiId))

            // Setup account name and address based on type
            when (account.type) {
                DAppEVMAccountType.COA -> {
                    tvAccountName.text = emoji.emojiName
                    tvAccountAddress.text = "(${shortenEVMString(account.address.toAddress())})"
                    tvEvmLabel.apply {
                        setVisible(true)
                        text = context.getString(R.string.label_evm)
                        backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.accent_purple))
                    }
                }
                DAppEVMAccountType.EOA -> {
                    tvAccountName.text = emoji.emojiName
                    tvAccountAddress.text = "(${shortenEVMString(account.address.toAddress())})"
                    tvEvmLabel.apply {
                        setVisible(true)
                        text = context.getString(R.string.label_eoa)
                        backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.accent_orange))
                    }
                }
            }

            // Setup balance - hide for EOA, show for COA
            when (account.type) {
                DAppEVMAccountType.COA -> {
                    tvAccountBalance.text = account.balance ?: "$0.00"
                    tvAccountBalance.setVisible(true)
                }
                DAppEVMAccountType.EOA -> {
                    tvAccountBalance.setVisible(false)
                }
            }
        }
    }

    fun setCallbacks(onConnect: () -> Unit, onCancel: () -> Unit) {
        this.onConnectCallback = onConnect
        this.onCancelCallback = onCancel
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _selectedAccountBinding = null
        _binding = null
    }
}
