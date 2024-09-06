package com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.presenter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.transition.Fade
import android.transition.Scene
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentWalletRestoreMnemonicBinding
import com.flowfoundation.wallet.page.main.MainActivity
import com.flowfoundation.wallet.page.walletrestore.AccountNotFoundDialog
import com.flowfoundation.wallet.page.walletrestore.ERROR_ACCOUNT_NOT_FOUND
import com.flowfoundation.wallet.page.walletrestore.WALLET_RESTORE_ERROR
import com.flowfoundation.wallet.page.walletrestore.WalletRestoreViewModel
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.WalletRestoreMnemonicFragment
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.WalletRestoreMnemonicViewModel
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.adapter.MnemonicSuggestAdapter
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.model.WalletRestoreMnemonicModel
import com.flowfoundation.wallet.page.walletrestore.requestWalletRestoreLogin
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.listeners.SimpleTextWatcher
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration
import kotlinx.coroutines.delay
import wallet.core.jni.HDWallet

class WalletRestoreMnemonicPresenter(
    private val fragment: WalletRestoreMnemonicFragment,
    private val binding: FragmentWalletRestoreMnemonicBinding,
) : BasePresenter<WalletRestoreMnemonicModel> {
    private val keyboardObserver by lazy { keyboardObserver() }
    private val rootView by lazy { fragment.requireActivity().findViewById<View>(R.id.rootView) }

    private val mnemonicAdapter by lazy { MnemonicSuggestAdapter() }

    private val viewModel by lazy { ViewModelProvider(fragment.requireActivity())[WalletRestoreMnemonicViewModel::class.java] }
    private val pageViewModel by lazy { ViewModelProvider(fragment.requireActivity())[WalletRestoreViewModel::class.java] }

    private val errorColor by lazy { R.color.error.res2color() }

    private val context = fragment.requireContext()

    init {
        observeKeyboardVisible()
        with(binding.recyclerView) {
            adapter = mnemonicAdapter
            layoutManager = LinearLayoutManager(fragment.requireContext(), LinearLayoutManager.HORIZONTAL, false)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 10.dp2px().toInt(), LinearLayout.HORIZONTAL))
        }

        binding.editText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                viewModel.suggestMnemonic(s.toString())
                viewModel.invalidMnemonicCheck(s.toString(), isLegacy = true)
            }
        })
        binding.nextButton.setOnClickListener {
            binding.nextButton.setProgressVisible(true)
            login()
        }
    }

    private fun login() {
        uiScope {
            val mnemonic = formatMnemonic()
            if (!isMnemonicVerify(mnemonic)) {
                toast(msgRes = R.string.mnemonic_incorrect)
                binding.nextButton.setProgressVisible(false)
            }
            requestWalletRestoreLogin(mnemonic) { isSuccess, reason ->
                uiScope {
                    if (isSuccess) {
                        delay(200)
                        MainActivity.relaunch(context, clearTop = true)
                    } else {
                        binding.nextButton.setProgressVisible(false)
                        if (reason == ERROR_ACCOUNT_NOT_FOUND) {
                            AccountNotFoundDialog(context, mnemonic).show()
                        } else {
                            pageViewModel.changeStep(WALLET_RESTORE_ERROR)
                        }
                    }
                }
            }
        }
    }

    override fun bind(model: WalletRestoreMnemonicModel) {
        model.mnemonicList?.let { mnemonicAdapter.setNewDiffData(it) }
        model.selectSuggest?.let { selectSuggest(it) }
        model.invalidWordList?.let { invalidWord(it) }
    }

    private fun invalidWord(array: List<Pair<Int, String>>) {
        binding.stateIcon.setVisible(array.isNotEmpty())
        binding.stateText.setVisible(array.isNotEmpty())
        binding.editText.backgroundTintList = ColorStateList.valueOf(if (array.isEmpty()) R.color.text.res2color() else R.color.error.res2color())
        with(binding.editText) {
            val selection = Selection.getSelectionStart(text)
            val sp = SpannableStringBuilder(text.toString())
            array.forEach { word ->
                val start = word.first
                val end = word.first + word.second.length
                if (sp.length >= end) {
                    sp.setSpan(ForegroundColorSpan(errorColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            text = sp
            setSelection(selection)
        }

        val isComplete = array.isEmpty() && formatMnemonic().split(" ").size == 12
        binding.nextButton.isEnabled = isComplete
    }

    private fun formatMnemonic() = binding.editText.text.split(" ").filter { it.isNotBlank() }.joinToString(" ") { it }

    @SuppressLint("SetTextI18n")
    private fun selectSuggest(word: String) {
        with(binding.editText) {
            val text = text.split(" ").dropLast(1).toMutableList().apply { add(word) }
            setText(text.joinToString(" ") { it } + " ")
            setSelection(getText().length)
        }
    }

    fun unbind() {
        with(rootView.viewTreeObserver) {
            if (isAlive) {
                removeOnGlobalLayoutListener(keyboardObserver)
            }
        }
    }

    private fun observeKeyboardVisible() {
        rootView.post { rootView.viewTreeObserver.addOnGlobalLayoutListener(keyboardObserver) }
    }

    private fun keyboardObserver(): ViewTreeObserver.OnGlobalLayoutListener {
        return ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val contentHeight = rootView.rootView.height

            val isKeyboardVisible = contentHeight - rect.bottom > contentHeight * 0.15f
            TransitionManager.go(Scene(rootView as ViewGroup), Fade().apply { duration = 150 })
            binding.nextButton.setVisible(!isKeyboardVisible)
            binding.recyclerView.setVisible(isKeyboardVisible)
        }
    }

    private fun isMnemonicVerify(mnemonic: String): Boolean {
        return try {
            HDWallet(mnemonic, "")
            true
        } catch (e: Exception) {
            false
        }
    }
}