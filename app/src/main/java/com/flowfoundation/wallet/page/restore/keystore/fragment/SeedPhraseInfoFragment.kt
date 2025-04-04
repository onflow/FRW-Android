package com.flowfoundation.wallet.page.restore.keystore.fragment

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.transition.Fade
import android.transition.Scene
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentSeedPhraseInfoBinding
import com.flowfoundation.wallet.page.restore.keystore.viewmodel.KeyStoreRestoreViewModel
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.WalletRestoreMnemonicViewModel
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.adapter.MnemonicSuggestAdapter
import com.flowfoundation.wallet.utils.addressPattern
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.listeners.SimpleTextWatcher
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration
import wallet.core.jni.HDWallet


class SeedPhraseInfoFragment: Fragment() {
    private lateinit var binding: FragmentSeedPhraseInfoBinding
    private val restoreViewModel by lazy {
        ViewModelProvider(requireActivity())[KeyStoreRestoreViewModel::class.java]
    }
    private lateinit var mnemonicViewModel: WalletRestoreMnemonicViewModel
    private val keyboardObserver by lazy { keyboardObserver() }
    private val rootView by lazy { requireActivity().findViewById<View>(R.id.rootView) }

    private val mnemonicAdapter by lazy { MnemonicSuggestAdapter() }

    private val errorColor by lazy { R.color.error.res2color() }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSeedPhraseInfoBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        observeKeyboardVisible()
        mnemonicViewModel = ViewModelProvider(requireActivity())[WalletRestoreMnemonicViewModel::class.java].apply {
            mnemonicSuggestListLiveData.observe(viewLifecycleOwner) {
                mnemonicAdapter.setNewDiffData(it)
            }
            selectSuggestLiveData.observe(viewLifecycleOwner) {
                this@SeedPhraseInfoFragment.selectSuggest(it)
            }
            invalidWordListLiveData.observe(viewLifecycleOwner) {
                invalidWord(it)
            }
        }
        with(binding.recyclerView) {
            adapter = mnemonicAdapter
            layoutManager = LinearLayoutManager(
                requireContext(),
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
        with(binding) {
            clAdvanced.setOnClickListener {
                if (clAdvancedLayout.isVisible()) {
                    ivAdvanced.setImageResource(R.drawable.ic_seed_phrase_advanced_open)
                    clAdvancedLayout.gone()
                } else {
                    ivAdvanced.setImageResource(R.drawable.ic_seed_phrase_advanced_close)
                    clAdvancedLayout.visible()
                }
            }
            etSeedPhrase.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    mnemonicViewModel.suggestMnemonic(s.toString())
                    mnemonicViewModel.invalidMnemonicCheck(s.toString())
                }
            })
            btnImport.setOnClickListener {
                val mnemonic = formatMnemonic()
                val passphrase = etPassphrase.text.toString().trim()
                if (!isMnemonicVerify(mnemonic, passphrase)) {
                    toast(msgRes = R.string.mnemonic_incorrect)
                    return@setOnClickListener
                }
                val address = etAddress.text.toString().trim()
                if (address.isNotEmpty() && addressPattern.matches(address).not()) {
                    toast(msgRes = R.string.address_incorrect)
                    return@setOnClickListener
                }
                val derivationPath = etDerivationPath.text.toString().trim()
                restoreViewModel.importSeedPhrase(
                    mnemonic,
                    address,
                    passphrase,
                    derivationPath.ifEmpty {
                        "m/44'/539'/0'/0/0"
                    }
                )
            }
            btnImport.isEnabled = false
        }
    }

    private fun invalidWord(array: List<Pair<Int, String>>) {
        binding.stateIcon.setVisible(array.isNotEmpty())
        binding.stateText.setVisible(array.isNotEmpty())
        with(binding.etSeedPhrase) {
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
        val mnemonicSize = formatMnemonic().split(" ").size

        val isComplete = array.isEmpty() && (mnemonicSize == 15 || mnemonicSize == 12)
        binding.btnImport.isEnabled = isComplete
    }

    private fun formatMnemonic() =
        binding.etSeedPhrase.text.split(" ").filter { it.isNotBlank() }.joinToString(" ") { it }

    @SuppressLint("SetTextI18n")
    private fun selectSuggest(word: String) {
        with(binding.etSeedPhrase) {
            val text = text.split(" ").dropLast(1).toMutableList().apply { add(word) }
            setText(text.joinToString(" ") { it } + " ")
            setSelection(getText().length)
        }
    }

    private fun keyboardObserver(): ViewTreeObserver.OnGlobalLayoutListener {
        return ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val contentHeight = rootView.rootView.height

            val isKeyboardVisible = contentHeight - rect.bottom > contentHeight * 0.15f
            TransitionManager.go(Scene(rootView as ViewGroup), Fade().apply { duration = 150 })
            binding.btnImport.setVisible(!isKeyboardVisible)
            binding.recyclerView.setVisible(isKeyboardVisible)
        }
    }

    private fun observeKeyboardVisible() {
        rootView.post { rootView.viewTreeObserver.addOnGlobalLayoutListener(keyboardObserver) }
    }

    private fun isMnemonicVerify(mnemonic: String, passphrase: String): Boolean {
        return try {
            HDWallet(mnemonic, passphrase)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroyView() {
        with(rootView.viewTreeObserver) {
            if (isAlive) {
                removeOnGlobalLayoutListener(keyboardObserver)
            }
        }
        super.onDestroyView()
    }
}