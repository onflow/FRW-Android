package com.flowfoundation.wallet.page.token.detail.widget

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.databinding.DialogSelectTokenBinding
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.page.swap.dialog.select.TokenListAdapter
import com.flowfoundation.wallet.page.token.detail.model.MoveToken
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.math.BigDecimal

class SelectMoveTokenDialog : BottomSheetDialogFragment() {

    private var selectedCoin: String? = null
    private var disableCoin: String? = null
    private var result: Continuation<FlowCoin?>? = null
    private var moveFromAddress: String? = null

    private lateinit var binding: DialogSelectTokenBinding

    private val adapter by lazy {
        TokenListAdapter(selectedCoin, disableCoin) { token ->
            result?.resume(token.tokenInfo)
            dismiss()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSelectTokenBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        result ?: return
        with(binding.tokenList) {
            adapter = this@SelectMoveTokenDialog.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                ColorDividerItemDecoration(Color.TRANSPARENT, 12.dp2px().toInt())
            )
        }
        val list = FlowCoinListManager.coinList()
            .filter { it.isFlowCoin() || it.canBridgeToCOA() || it.isCOABridgeCoin() }
            .map { coin -> MoveToken(BigDecimal.ZERO, coin) }
        adapter.setNewDiffData(list)
    }

    override fun onCancel(dialog: DialogInterface) {
        result?.resume(null)
    }

    suspend fun show(
        selectedCoin: String?,
        disableCoin: String?,
        fragmentManager: FragmentManager,
        moveFromAddress: String? = null
    ) = suspendCoroutine { result ->
        this.selectedCoin = selectedCoin
        this.disableCoin = disableCoin
        this.result = result
        this.moveFromAddress = moveFromAddress
        show(fragmentManager, "")
    }

    override fun onResume() {
        if (result == null) {
            dismiss()
        }
        super.onResume()
    }
}