package com.flowfoundation.wallet.widgets

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.manager.app.doNetworkChangeTask
import com.flowfoundation.wallet.manager.app.refreshChainNetworkSync
import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import com.flowfoundation.wallet.manager.flowjvm.FlowApi
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.clearUserCache
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.utils.NETWORK_MAINNET
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.utils.updateChainNetworkPreference
import kotlinx.coroutines.delay

enum class DialogType(
    val descResId: Int
) {
    BACKUP(R.string.backup_on_mainnet),
    RESTORE(R.string.restore_on_mainnet),
    SWITCH(R.string.switch_on_mainnet),
    CREATE(R.string.create_on_mainnet_or_testnet)
}

class SwitchNetworkDialog(
    private val context: Context,
    private val dialogType: DialogType
) {
    fun show() {
        var dialog: Dialog? = null
        with(AlertDialog.Builder(context, R.style.Theme_AlertDialogTheme)) {
            setView(SwitchNetworkDialogView(context, dialogType) { dialog?.cancel() })
            with(create()) {
                dialog = this
                show()
            }
        }
    }
}

@SuppressLint("ViewConstructor")
private class SwitchNetworkDialogView(
    context: Context,
    dialogType: DialogType,
    private val onCancel: () -> Unit,
) : FrameLayout(context) {

    private val switchButton by lazy { findViewById<View>(R.id.switch_button) }
    private val cancelButton by lazy { findViewById<View>(R.id.cancel_button) }
    private val descView by lazy { findViewById<TextView>(R.id.desc_view) }

    init {
        LayoutInflater.from(context).inflate(R.layout.dialog_switch_network, this)

        descView.text = dialogType.descResId.res2String()

        switchButton.setOnClickListener {
            updateChainNetworkPreference(NETWORK_MAINNET) {
                ioScope {
                    delay(200)
                    refreshChainNetworkSync()
                    doNetworkChangeTask()
                    FlowApi.refreshConfig()
                    FlowCadenceApi.refreshConfig()
                    uiScope {
                        FlowLoadingDialog(context).show()
                        onCancel()
                        WalletManager.changeNetwork()
                        clearUserCache()
                        MainActivity.relaunch(context, true)
                    }
                }
            }
        }
        cancelButton.setOnClickListener { onCancel() }
    }
}
