package io.outblock.lilico.widgets

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import io.outblock.lilico.R
import io.outblock.lilico.manager.app.doNetworkChangeTask
import io.outblock.lilico.manager.app.refreshChainNetworkSync
import io.outblock.lilico.manager.flowjvm.FlowApi
import io.outblock.lilico.manager.wallet.WalletManager
import io.outblock.lilico.network.clearUserCache
import io.outblock.lilico.page.main.MainActivity
import io.outblock.lilico.utils.NETWORK_MAINNET
import io.outblock.lilico.utils.extensions.res2String
import io.outblock.lilico.utils.ioScope
import io.outblock.lilico.utils.uiScope
import io.outblock.lilico.utils.updateChainNetworkPreference
import kotlinx.coroutines.delay

enum class DialogType(
    val descResId: Int
) {
    BACKUP(R.string.backup_on_mainnet),
    RESTORE(R.string.restore_on_mainnet),
    SWITCH(R.string.switch_on_mainnet),
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
                    ioScope {
                        FlowApi.refreshConfig()
                        uiScope {
                            onCancel()
                            WalletManager.changeNetwork()
                            clearUserCache()
                            MainActivity.relaunch(context, true)
                        }
                    }
                }
            }
        }
        cancelButton.setOnClickListener { onCancel() }
    }
}
