package com.flowfoundation.wallet.page.receive.weight

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.flowfoundation.wallet.databinding.LayoutSwitchVmBinding
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.utils.extensions.setVisible


class SwitchVMLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = LayoutSwitchVmBinding.inflate(LayoutInflater.from(context))
    private var onVMSwitchListener: ((isSwitchToEVM: Boolean) -> Unit)? = null

    init {
        addView(binding.root)

        with(binding) {
            clEvm.setOnClickListener {
                changeSwitchLayout(true)
                onVMSwitchListener?.invoke(true)
            }

            clCadence.setOnClickListener {
                changeSwitchLayout(false)
                onVMSwitchListener?.invoke(false)
            }

            changeSwitchLayout(WalletManager.isEVMAccountSelected())
        }
    }

    fun setOnVMSwitchListener(switchListener: ((isSwitchToEVM: Boolean) -> Unit)) {
        this.onVMSwitchListener = switchListener
    }

    private fun changeSwitchLayout(isEVM: Boolean) {
        binding.groupEvmSwitched.setVisible(isEVM)
        binding.groupCadenceSwitched.setVisible(isEVM.not())
    }

}