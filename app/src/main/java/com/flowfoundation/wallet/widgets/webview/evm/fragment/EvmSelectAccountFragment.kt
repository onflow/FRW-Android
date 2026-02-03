package com.flowfoundation.wallet.widgets.webview.evm.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentEvmSelectAccountBinding
import com.flowfoundation.wallet.databinding.ItemSelectedEvmAccountBinding
import com.flowfoundation.wallet.databinding.ItemUnselectedEvmAccountBinding
import com.flowfoundation.wallet.manager.emoji.AccountEmojiManager
import com.flowfoundation.wallet.manager.emoji.model.Emoji
import com.flowfoundation.wallet.manager.evm.DAppEVMAccount
import com.flowfoundation.wallet.manager.evm.DAppEVMAccountType
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.shortenEVMString
import com.flowfoundation.wallet.wallet.toAddress
import com.flowfoundation.wallet.widgets.webview.evm.viewmodel.EVMDialogViewModel
import kotlinx.coroutines.launch
import androidx.core.view.isNotEmpty
import com.flowfoundation.wallet.utils.extensions.gone

class EvmSelectAccountFragment : Fragment() {
    private val TAG = EvmSelectAccountFragment::class.java.simpleName

    private var _binding: FragmentEvmSelectAccountBinding? = null
    private val binding get() = _binding!!

    private var _selectedAccountBinding: ItemSelectedEvmAccountBinding? = null

    private val viewModel: EVMDialogViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEvmSelectAccountBinding.inflate(inflater, container, false)

        // Create binding for the selected account layout
        _selectedAccountBinding = ItemSelectedEvmAccountBinding.bind(binding.flSelectedAccount.getChildAt(0))

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedAccount.collect { selectedAccount ->
                selectedAccount?.let { setupSelectedAccount(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.availableAccounts.collect { accounts ->
                setupUnselectedAccounts(accounts)
            }
        }
    }

    private fun setupClickListeners() {
        binding.closeButton.setOnClickListener {
            logd(TAG, "Close button clicked, returning to main account view")
            viewModel.showMainAccount()
        }
    }

    private fun setupSelectedAccount(account: DAppEVMAccount) {
        logd(TAG, "Setting up selected account: ${account.address}")

        val selectedBinding = _selectedAccountBinding ?: return
        setupAccountItem(selectedBinding, account)
    }

    private fun setupUnselectedAccounts(accounts: List<DAppEVMAccount>) {
        val selectedAccount = viewModel.selectedAccount.value
        val unselectedAccounts = accounts.filter { it.address != selectedAccount?.address }

        logd(TAG, "Setting up ${unselectedAccounts.size} unselected accounts")

        // Clear existing views first
        val unselectedContainer = binding.flUnselectedAccount
        unselectedContainer.removeAllViews()

        if (unselectedAccounts.isEmpty()) {
            logd(TAG, "No unselected accounts found")
            binding.flUnselectedAccount.setVisible(false)
            return
        }

        binding.flUnselectedAccount.setVisible(true)

        // Add each unselected account
        unselectedAccounts.forEach { account ->
            val accountItemView = layoutInflater.inflate(
                R.layout.item_unselected_evm_account,
                unselectedContainer,
                false
            )

            val accountBinding = ItemUnselectedEvmAccountBinding.bind(accountItemView)
            setupUnselectedAccountItem(accountBinding, account)

            // Set click listener for this account
            accountItemView.setOnClickListener {
                logd(TAG, "Unselected account clicked: ${account.address}")
                viewModel.selectAccount(account)
            }

            // Add margin between items (except for the first one)
            if (unselectedContainer.isNotEmpty()) {
                val layoutParams = accountItemView.layoutParams as LinearLayout.LayoutParams
                layoutParams.topMargin = (12 * resources.displayMetrics.density).toInt() // 12dp
                accountItemView.layoutParams = layoutParams
            }

            unselectedContainer.addView(accountItemView)
        }

        logd(TAG, "Added ${unselectedAccounts.size} unselected account views")
    }

    private fun setupAccountItem(
        accountBinding: ItemSelectedEvmAccountBinding,
        account: DAppEVMAccount
    ) {
        val emoji = AccountEmojiManager.getEmojiByAddress(account.address)

        with(accountBinding) {
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

            walletSelectedView.gone()
        }
    }

    private fun setupUnselectedAccountItem(
        accountBinding: ItemUnselectedEvmAccountBinding,
        account: DAppEVMAccount
    ) {
        val emoji = AccountEmojiManager.getEmojiByAddress(account.address)

        with(accountBinding) {
            // Setup emoji icon
            tvAccountIcon.text = Emoji.getEmojiById(emoji.emojiId)
            tvAccountIcon.backgroundTintList = ColorStateList.valueOf(Emoji.getEmojiColorRes(emoji.emojiId))

            // Setup account name and address based on type
            when (account.type) {
                DAppEVMAccountType.COA -> {
                    tvAccountName.text = emoji.emojiName
                    tvAccountAddress.text = shortenEVMString(account.address.toAddress())
                    tvEvmLabel.apply {
                        setVisible(true)
                        text = context.getString(R.string.label_evm)
                        backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.accent_purple))
                    }
                }
                DAppEVMAccountType.EOA -> {
                    tvAccountName.text = emoji.emojiName
                    tvAccountAddress.text = shortenEVMString(account.address.toAddress())
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

    override fun onDestroyView() {
        super.onDestroyView()
        _selectedAccountBinding = null
        _binding = null
    }
}
