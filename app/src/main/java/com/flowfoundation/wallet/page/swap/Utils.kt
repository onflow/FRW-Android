package com.flowfoundation.wallet.page.swap

import android.annotation.SuppressLint
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.activity.BaseActivity
import com.flowfoundation.wallet.databinding.ActivitySwapBinding
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.network.model.SwapEstimateResponse
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.toSafeFloat
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.utils.formatNum
import com.flowfoundation.wallet.utils.formatPrice


fun ActivitySwapBinding.viewModel(): SwapViewModel {
    return ViewModelProvider(findActivity(root) as FragmentActivity)[SwapViewModel::class.java]
}

fun ActivitySwapBinding.bindInputListener() {
    bindFromListener()
    bindToListener()
}

fun ActivitySwapBinding.updateFromCoin(coin: FlowCoin) {
    Glide.with(fromCoinIcon).load(coin.icon()).into(fromCoinIcon)
    fromCoinName.text = coin.symbol.uppercase()
    legalCheck()
}

fun ActivitySwapBinding.updateToCoin(coin: FlowCoin) {
    Glide.with(toCoinIcon).load(coin.icon()).into(toCoinIcon)
    toCoinName.text = coin.symbol.uppercase()
    toButton.strokeWidth = 0
    legalCheck()
}

fun ActivitySwapBinding.updateFromAmount(amount: Float) {
    fromInput.setText(amount.formatNum())
}

fun ActivitySwapBinding.updateToAmount(amount: Float) {
    toInput.setText(amount.formatNum())
}

fun ActivitySwapBinding.updateProgressState(isLoading: Boolean) {
    progressBar.setVisible(isLoading)
    switchButton.setVisible(!isLoading, invisible = true)
    if (isLoading) swapButton.isEnabled = false
}

fun ActivitySwapBinding.switchCoin() {
    val fromAmount = fromAmount()
    val toAmount = toAmount()

    if (fromInput.hasFocus() || toInput.hasFocus()) {
        if (fromInput.hasFocus()) {
            fromInput.clearFocus()
            toInput.requestFocus()
        } else {
            toInput.clearFocus()
            fromInput.requestFocus()
        }
    }

    fromInput.setText(if (toInput.text.isEmpty()) "" else "$toAmount")
    toInput.setText(if (fromInput.text.isEmpty()) "" else "$fromAmount")

    if (fromInput.hasFocus()) fromInput.setSelection(fromInput.length())
    if (toInput.hasFocus()) toInput.setSelection(toInput.length())
}

@SuppressLint("SetTextI18n")
fun ActivitySwapBinding.updateEstimate(data: SwapEstimateResponse.Data) {
    val viewModel = viewModel()
    val fromCoin = viewModel.fromCoin() ?: return
    val toCoin = viewModel.toCoin() ?: return

    val amountIn = data.routes.firstOrNull()?.routeAmountIn ?: return
    val amountOut = data.routes.firstOrNull()?.routeAmountOut ?: return
    convertView.setVisible(true)
    convertView.text = "1 ${fromCoin.symbol.uppercase()} ≈ ${(amountOut / amountIn).formatNum()} ${toCoin.symbol.uppercase()}"
}

private fun ActivitySwapBinding.bindFromListener() {
    with(fromInput) {
        doOnTextChanged { _, _, _, _ ->
            updateAmountPrice()
            legalCheck()
            if (!hasFocus()) return@doOnTextChanged
            viewModel().updateExactToken(ExactToken.FROM)
        }
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                hideKeyboard()
                legalCheck()
            }
            return@setOnEditorActionListener false
        }
    }
}

private fun ActivitySwapBinding.bindToListener() {
    with(toInput) {
        doOnTextChanged { _, _, _, _ ->
            legalCheck()
            if (!hasFocus()) return@doOnTextChanged
            viewModel().updateExactToken(ExactToken.TO)
        }
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                hideKeyboard()
                legalCheck()
            }
            return@setOnEditorActionListener false
        }
    }
}

fun ActivitySwapBinding.onBalanceUpdate() {
    legalCheck()
}

fun ActivitySwapBinding.onCoinRateUpdate() {
    updateAmountPrice()
}

private fun ActivitySwapBinding.updateAmountPrice() {
    val amount = fromInput.text.toString().toSafeFloat()
    priceAmountView.text = (viewModel().fromCoinRate() * amount).formatPrice(includeSymbol = true, includeSymbolSpace = true)
}

private fun ActivitySwapBinding.legalCheck() {
    val viewModel = viewModel()
    val balance = viewModel.fromCoinBalance()
    if (fromAmount() > balance) {
        swapButton.isEnabled = false
        swapButton.setText(R.string.insufficient_balance)
        return
    }

    if (fromAmount() == 0.0f) {
        swapButton.isEnabled = false
        swapButton.setText(R.string.swap)
        return
    }

    swapButton.setText(R.string.swap)
    swapButton.isEnabled = toAmount() > 0
}

fun swapPageBinding(): ActivitySwapBinding? {
    if (BaseActivity.getCurrentActivity()?.javaClass != SwapActivity::class.java) {
        return null
    }

    val activity = BaseActivity.getCurrentActivity() ?: return null

    return ActivitySwapBinding.bind(activity.findViewById(R.id.root_view))
}

fun ActivitySwapBinding.fromAmount() = fromInput.text.toString().toSafeFloat()
fun ActivitySwapBinding.toAmount() = toInput.text.toString().toSafeFloat()

fun ActivitySwapBinding.setMaxAmount() {
    val viewModel = viewModel()
    val balance = viewModel.fromCoinBalance()
    fromInput.requestFocus()
    fromInput.setText(balance.formatNum())
    fromInput.setSelection(fromInput.length())
}