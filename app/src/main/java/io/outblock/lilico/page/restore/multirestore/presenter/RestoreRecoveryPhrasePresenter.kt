package io.outblock.lilico.page.restore.multirestore.presenter

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
import io.outblock.lilico.R
import io.outblock.lilico.base.presenter.BasePresenter
import io.outblock.lilico.databinding.FragmentRestoreRecoveryPhraseBinding
import io.outblock.lilico.page.restore.multirestore.fragment.RestoreRecoveryPhraseFragment
import io.outblock.lilico.page.restore.multirestore.viewmodel.MultiRestoreViewModel
import io.outblock.lilico.page.walletrestore.fragments.mnemonic.WalletRestoreMnemonicViewModel
import io.outblock.lilico.page.walletrestore.fragments.mnemonic.adapter.MnemonicSuggestAdapter
import io.outblock.lilico.page.walletrestore.fragments.mnemonic.model.WalletRestoreMnemonicModel
import io.outblock.lilico.utils.extensions.dp2px
import io.outblock.lilico.utils.extensions.res2color
import io.outblock.lilico.utils.extensions.setVisible
import io.outblock.lilico.utils.listeners.SimpleTextWatcher
import io.outblock.lilico.utils.toast
import io.outblock.lilico.widgets.itemdecoration.ColorDividerItemDecoration
import wallet.core.jni.HDWallet


class RestoreRecoveryPhrasePresenter(
    private val fragment: RestoreRecoveryPhraseFragment,
    private val binding: FragmentRestoreRecoveryPhraseBinding
) : BasePresenter<WalletRestoreMnemonicModel> {

    private val keyboardObserver by lazy { keyboardObserver() }
    private val rootView by lazy { fragment.requireActivity().findViewById<View>(R.id.rootView) }

    private val mnemonicAdapter by lazy { MnemonicSuggestAdapter() }

    private val restoreViewModel by lazy { ViewModelProvider(fragment.requireActivity())[MultiRestoreViewModel::class.java] }
    private val viewModel by lazy { ViewModelProvider(fragment.requireActivity())[WalletRestoreMnemonicViewModel::class.java] }

    private val errorColor by lazy { R.color.error.res2color() }


    init {
        observeKeyboardVisible()
        with(binding.recyclerView) {
            adapter = mnemonicAdapter
            layoutManager = LinearLayoutManager(
                fragment.requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            addItemDecoration(
                ColorDividerItemDecoration(
                    Color.TRANSPARENT,
                    10.dp2px().toInt(),
                    LinearLayout.HORIZONTAL
                )
            )
        }

        binding.editText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                viewModel.suggestMnemonic(s.toString())
                viewModel.invalidMnemonicCheck(s.toString())
            }
        })
        with(binding.nextButton) {
            setOnClickListener {
                if (isProgressVisible()) {
                    return@setOnClickListener
                }
                val mnemonic = formatMnemonic()
                if (!isMnemonicVerify(mnemonic)) {
                    toast(msgRes = R.string.mnemonic_incorrect)
                    return@setOnClickListener
                }
                restoreViewModel.addMnemonicToTransaction(mnemonic = mnemonic)
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
        binding.editText.backgroundTintList =
            ColorStateList.valueOf(if (array.isEmpty()) R.color.text.res2color() else R.color.error.res2color())
        with(binding.editText) {
            val selection = Selection.getSelectionStart(text)
            val sp = SpannableStringBuilder(text.toString())
            array.forEach { word ->
                sp.setSpan(
                    ForegroundColorSpan(errorColor),
                    word.first,
                    word.first + word.second.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            text = sp
            setSelection(selection)
        }

        val isComplete = array.isEmpty() && formatMnemonic().split(" ").size == 15
        binding.nextButton.isEnabled = isComplete
    }

    private fun formatMnemonic() =
        binding.editText.text.split(" ").filter { it.isNotBlank() }.joinToString(" ") { it }

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