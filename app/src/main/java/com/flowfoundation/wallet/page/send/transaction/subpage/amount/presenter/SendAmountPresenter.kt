package com.flowfoundation.wallet.page.send.transaction.subpage.amount.presenter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.transition.Fade
import androidx.transition.Scene
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.zackratos.ultimatebarx.ultimatebarx.addNavigationBarBottomPadding
import com.zackratos.ultimatebarx.ultimatebarx.addStatusBarTopPadding
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.ActivitySendAmountBinding
import com.flowfoundation.wallet.manager.coin.FlowCoin
import com.flowfoundation.wallet.manager.coin.FlowCoinListManager
import com.flowfoundation.wallet.manager.flowjvm.cadenceQueryMinFlowBalance
import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.network.model.AddressBookContact
import com.flowfoundation.wallet.page.address.model.AddressBookPersonModel
import com.flowfoundation.wallet.page.address.presenter.AddressBookPersonPresenter
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.SendAmountActivity
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.SendAmountViewModel
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.SendAmountModel
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.SendBalanceModel
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.model.TransactionModel
import com.flowfoundation.wallet.page.send.transaction.subpage.amount.widget.SendCoinPopupMenu
import com.flowfoundation.wallet.page.send.transaction.subpage.transaction.TransactionDialog
import com.flowfoundation.wallet.utils.*
import com.flowfoundation.wallet.utils.extensions.*

class SendAmountPresenter(
    private val activity: SendAmountActivity,
    private val binding: ActivitySendAmountBinding,
    private val contact: AddressBookContact,
) : BasePresenter<SendAmountModel> {

    private val viewModel by lazy { ViewModelProvider(activity)[SendAmountViewModel::class.java] }

    private var minFlowBalance = 0f

    private fun balance() = viewModel.balanceLiveData.value

    init {
        setupToolbar()
        setupContactCard()
        with(binding.transferAmountInput) {
            doOnTextChanged { _, _, _, _ ->
                updateTransferAmountConvert()
                checkAmount()
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    hideKeyboard()
                    if (verifyAmount() && !binding.errorWrapper.isVisible()) showSendDialog()
                }
                return@setOnEditorActionListener false
            }
        }
        with(binding) {
            root.addStatusBarTopPadding()
            root.addNavigationBarBottomPadding()
            nextButton.setOnClickListener { showSendDialog() }
            cancelButton.setOnClickListener { activity.finish() }
            swapButton.setOnClickListener { viewModel.swapCoin() }
            coinWrapper.setOnClickListener { SendCoinPopupMenu(coinWrapper).show() }
            maxButton.setOnClickListener { setMaxAmount() }
        }
        getMinFlowBalance()
    }

    override fun bind(model: SendAmountModel) {
        model.balance?.let { updateBalance(it) }
        model.onCoinSwap?.let { updateCoinState() }
    }

    private fun getMinFlowBalance() {
        ioScope {
            minFlowBalance = try {
                cadenceQueryMinFlowBalance() ?: 0.001f
            } catch (e: Exception) {
                0.001f
            }
        }
    }

    private fun minBalance(): Float {
        return if (WalletManager.isEVMAccountSelected() || viewModel.currentCoin() != FlowCoin.SYMBOL_FLOW) {
            0f
        } else {
            minFlowBalance
        }
    }

    private fun updateCoinState() {
        with(binding) {
            Glide.with(coinIconView).load(viewModel.currentCoin().coinIcon()).into(coinIconView)
            if (viewModel.currentCoin() == selectedCurrency().flag) {
                coinIconView.imageTintList = ColorStateList.valueOf(R.color.note.res2color())
            } else {
                coinIconView.imageTintList = null
            }

            updateTransferAmountConvert()

            val icon =
                if (viewModel.currentCoin() == selectedCurrency().flag) FlowCoinListManager.getCoin(
                    viewModel.convertCoin()
                )?.icon else FlowCoinListManager.getCoin(
                    viewModel.currentCoin()
                )?.icon
            Glide.with(balanceIconView).load(icon).into(balanceIconView)
            coinWrapper.isEnabled = viewModel.currentCoin() != selectedCurrency().flag
            coinMoreArrowView.setVisible(viewModel.currentCoin() != selectedCurrency().flag)
        }
        checkAmount()
    }

    private fun checkAmount() {
        val amount = binding.transferAmountInput.text.ifBlank { "0" }.toString().toSafeFloat()
        val coinRate = (balance()?.coinRate ?: 0f) * CurrencyManager.currencyPrice()
        val inputBalance = if (viewModel.convertCoin() == selectedCurrency().flag) amount else amount / (if (coinRate == 0f) 1f else coinRate)
        val isOutOfBalance = inputBalance > ((balance()?.balance ?: 0f) - minBalance())
        if (isOutOfBalance && !binding.errorWrapper.isVisible()) {
            TransitionManager.go(Scene(binding.root as ViewGroup), Fade().apply { })
        } else if (!isOutOfBalance && binding.errorWrapper.isVisible()) {
            TransitionManager.go(Scene(binding.root as ViewGroup), Fade().apply { })
        }
        binding.errorWrapper.setVisible(isOutOfBalance)
        binding.nextButton.isEnabled = verifyAmount() && !isOutOfBalance
    }

    @SuppressLint("SetTextI18n")
    private fun updateBalance(balance: SendBalanceModel) {
        with(binding) {
            balanceAmountView.text =
                "${balance.balance.formatNum()} ${FlowCoinListManager.getCoin(balance.symbol)?.name?.capitalizeV2()} "
            balanceAmountConvertView.text =
                "≈ " + (if (balance.coinRate > 0) balance.coinRate * balance.balance else 0f).formatPrice(
                    includeSymbol = true,
                    includeSymbolSpace = true
                )
            transferAmountInput.text = transferAmountInput.text
            transferAmountInput.setSelection(transferAmountInput.text.length)
            updateTransferAmountConvert()
        }
    }

    private fun updateTransferAmountConvert() {
        with(binding) {
            Glide.with(convertAmountIconView).load(viewModel.convertCoin().coinIcon())
                .into(convertAmountIconView)
            if (viewModel.convertCoin() == selectedCurrency().flag) {
                convertAmountIconView.imageTintList =
                    ColorStateList.valueOf(R.color.note.res2color())
            } else {
                convertAmountIconView.imageTintList = null
            }
            convertAmountView.text = getAmountConvert()
        }
    }

    private fun showSendDialog() {
        ioScope {
            val inputAmount =
                binding.transferAmountInput.text.ifBlank { "0" }.toString().toSafeFloat()
            val rate = (balance()?.coinRate ?: 0f) * CurrencyManager.currencyPrice()
            val amount =
                if (viewModel.currentCoin() == selectedCurrency().flag) inputAmount / rate else inputAmount
            uiScope {
                TransactionDialog.newInstance(
                    TransactionModel(
                        amount = amount,
                        coinSymbol = if (viewModel.currentCoin() == selectedCurrency().flag) viewModel.convertCoin() else viewModel.currentCoin(),
                        target = viewModel.contact(),
                        fromAddress = WalletManager.selectedWalletAddress(),
                    )
                ).show(activity.supportFragmentManager, "")
            }
        }
    }

    private fun setupContactCard() {
        with(binding.contactCard.root) {
            AddressBookPersonPresenter(this).bind(AddressBookPersonModel(data = contact))
            setPadding(0, paddingTop, 0, paddingBottom)
            isClickable = false
        }
        binding.contactCard.addButton.setVisible(false)
        binding.closeButton.setOnClickListener { activity.finish() }
    }

    private fun setMaxAmount() {
        val balance = (balance()?.balance ?: 0f) - minBalance()
        val coinRate = balance()?.coinRate ?: 0f
        val amount = (if (viewModel.convertCoin() == selectedCurrency().flag) balance else balance * coinRate).formatNum()
        with(binding) {
            transferAmountInput.setText(amount)
            transferAmountInput.setSelection(transferAmountInput.text.length)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.navigationIcon?.mutate()?.setTint(R.color.neutrals1.res2color())
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setDisplayShowHomeEnabled(true)
        activity.title = R.string.send_to.res2String()
    }

    private fun verifyAmount(): Boolean {
        val number = binding.transferAmountInput.text.toString().toFloatOrNull()
        return number != null && number > 0
    }

    private fun getAmountConvert(): String {
        val amount = binding.transferAmountInput.text.ifBlank { "0" }.toString().toSafeFloat()
        val rate = (balance()?.coinRate ?: 0f) * CurrencyManager.currencyPrice()
        val convert =
            if (viewModel.convertCoin() == selectedCurrency().flag) amount * rate else amount / rate
        return convert.formatNum()
    }

    private fun String.coinIcon(): Any {
        return FlowCoinListManager.getCoin(this)?.icon ?: selectedCurrency().icon
    }

}