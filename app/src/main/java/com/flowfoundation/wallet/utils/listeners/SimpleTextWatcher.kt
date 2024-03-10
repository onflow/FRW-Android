package com.flowfoundation.wallet.utils.listeners

import android.text.Editable
import android.text.TextWatcher

/**
 * @author John
 * @since 2020-01-28 15:57
 */
abstract class SimpleTextWatcher : TextWatcher {
    override fun afterTextChanged(s: Editable?) {
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }
}