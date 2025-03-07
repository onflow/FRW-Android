package com.flowfoundation.wallet.page.walletcreate.fragments.username

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.databinding.FragmentWalletCreateUsernameBinding
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.page.landing.LandingActivity
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.listeners.SimpleTextWatcher

class WalletCreateUsernamePresenter(
    private val fragment: Fragment,
    private val binding: FragmentWalletCreateUsernameBinding,
) : BasePresenter<WalletCreateUsernameModel> {

    private val viewModel by lazy { ViewModelProvider(fragment)[WalletCreateUsernameViewModel::class.java] }

    private val rootView by lazy { fragment.requireActivity().findViewById<View>(R.id.rootView) }

    private val keyboardObserver by lazy { keyboardObserver() }

    init {
        binding.editText.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                hideStateView()
                binding.progressBar.setVisible(true)
                binding.nextButton.isEnabled = false
                viewModel.verifyUsername(s.toString())
            }
        })
        with(binding.root) {
            post { layoutParams.height = height }
        }
        binding.nextButton.setOnClickListener {
            binding.editText.hideKeyboard()
            binding.nextButton.setProgressVisible(true)
            viewModel.createUser(binding.editText.text.toString())
        }
        observeKeyboardVisible()
    }

    override fun bind(model: WalletCreateUsernameModel) {
        model.state?.let { updateState(it) }
        model.createUserSuccess?.let { onCreateUserCallback(it) }
    }

    fun unbind() {
        with(rootView.viewTreeObserver) {
            if (isAlive) {
                removeOnGlobalLayoutListener(keyboardObserver)
            }
        }
    }

    private fun updateState(state: Pair<Boolean, String>) {
        if (state.second.isEmpty()) {
            hideStateView()
            return
        }
        binding.progressBar.setVisible(false)
        with(binding) {
            stateText.setVisible(true)
            stateIcon.setVisible(true)
            nextButton.isEnabled = state.first
            if (state.first) {
                stateText.setTextColor(R.color.text.res2color())
                stateIcon.setImageResource(R.drawable.ic_username_success)
            } else {
                stateText.setTextColor(R.color.text_sub.res2color())
                stateIcon.setImageResource(R.drawable.ic_username_error)
            }
            stateText.text = state.second
        }
    }

    private fun onCreateUserCallback(isSuccess: Boolean) {
        if (isSuccess) {
            MixpanelManager.accountCreationStart()
            LandingActivity.launch(binding.root.context)
        } else {
            binding.nextButton.setProgressVisible(false)
        }
    }

    private fun hideStateView() {
        binding.stateText.setVisible(false)
        binding.stateIcon.setVisible(false)
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
            with(binding.root) {
                binding.root.setPadding(
                    paddingLeft,
                    paddingTop,
                    paddingRight,
                    if (isKeyboardVisible) contentHeight - rect.bottom - 70.dp2px().toInt() else 0,
                )
            }

            binding.guideline.setGuidelinePercent(if (isKeyboardVisible) 0.02f else 0.2f)
        }
    }
}