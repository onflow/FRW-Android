package com.flowfoundation.wallet.page.nft.search.widget

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.flowfoundation.wallet.databinding.LayoutNftListSearchBinding
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.setVisible


class NFTListSearchLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = LayoutNftListSearchBinding.inflate(LayoutInflater.from(context))
    private var onSearchListener: OnSearchListener? = null

    init {
        addView(binding.root)
        setupListeners()
    }

    private fun setupListeners() {
        with(binding) {
            etSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    ivClear.setVisible(s?.isNotEmpty() == true)
                    onSearchListener?.onSearch(s?.toString().orEmpty())
                }
            })
            ivClear.setOnClickListener {
                etSearch.setText("")
                ivClear.gone()
                onSearchListener?.onSearch("")
            }
            etSearch.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    onSearchListener?.onSearch(etSearch.text.toString())
                    etSearch.hideKeyboard()
                    return@setOnEditorActionListener true
                }
                false
            }
        }
    }

    fun setOnSearchListener(listener: (String) -> Unit) {
        this.onSearchListener = object : OnSearchListener {
            override fun onSearch(keyword: String) {
                listener.invoke(keyword)
            }
        }
    }

}

interface OnSearchListener {
    fun onSearch(keyword: String)
}
