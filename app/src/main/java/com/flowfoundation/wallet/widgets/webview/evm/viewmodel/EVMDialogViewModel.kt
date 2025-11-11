package com.flowfoundation.wallet.widgets.webview.evm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowfoundation.wallet.manager.evm.DAppEVMAccount
import com.flowfoundation.wallet.manager.evm.DAppEVMConnectionManager
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMDialogModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EVMDialogViewModel : ViewModel() {
    private val TAG = EVMDialogViewModel::class.java.simpleName

    private val _dialogData = MutableStateFlow<EVMDialogModel?>(null)
    val dialogData: StateFlow<EVMDialogModel?> = _dialogData.asStateFlow()

    private val _selectedAccount = MutableStateFlow<DAppEVMAccount?>(null)
    val selectedAccount: StateFlow<DAppEVMAccount?> = _selectedAccount.asStateFlow()

    private val _availableAccounts = MutableStateFlow<List<DAppEVMAccount>>(emptyList())
    val availableAccounts: StateFlow<List<DAppEVMAccount>> = _availableAccounts.asStateFlow()

    private val _currentFragment = MutableStateFlow(FragmentType.ACCOUNT)
    val currentFragment: StateFlow<FragmentType> = _currentFragment.asStateFlow()

    enum class FragmentType {
        ACCOUNT,
        SELECT_ACCOUNT
    }

    init {
        observeManager()
    }

    fun setDialogData(data: EVMDialogModel) {
        logd(TAG, "Setting dialog data: ${data.title}")
        _dialogData.value = data
    }

    fun showSelectAccount() {
        logd(TAG, "Switching to select account fragment")
        _currentFragment.value = FragmentType.SELECT_ACCOUNT
    }

    fun showMainAccount() {
        logd(TAG, "Switching to main account fragment")
        _currentFragment.value = FragmentType.ACCOUNT
    }

    fun selectAccount(account: DAppEVMAccount) {
        logd(TAG, "Selecting account: ${account.address} (${account.type})")
        _selectedAccount.value = account
        _currentFragment.value = FragmentType.ACCOUNT

        // Update the manager with selected account
        DAppEVMConnectionManager.setSelectedAccount(account)
    }

    private fun observeManager() {
        viewModelScope.launch {
            DAppEVMConnectionManager.selectedAccount.collect { selectedAccount ->
                _selectedAccount.value = selectedAccount
            }
        }

        viewModelScope.launch {
            DAppEVMConnectionManager.availableAccounts.collect { accounts ->
                _availableAccounts.value = accounts
            }
        }
    }
}
