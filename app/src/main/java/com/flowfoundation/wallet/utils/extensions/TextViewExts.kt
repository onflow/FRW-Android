package com.flowfoundation.wallet.utils.extensions

import android.graphics.BlurMaskFilter
import android.graphics.MaskFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.style.MaskFilterSpan
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


/**
 * @author John
 * @since 2020-04-04 10:01
 */

fun TextView.isEllipsized(): Boolean {
    if (layout != null) {
        val lines: Int = layout.lineCount
        return lines > 0 && layout.getEllipsisCount(lines - 1) > 0 || lines > maxLines
    }
    return false
}

fun TextView.setBlurText(text: CharSequence, blurLevel: Float = 3f) {
    if (blurLevel == 0f) {
        setText(text)
        return
    }
    val blurMask: MaskFilter = BlurMaskFilter(blurLevel, BlurMaskFilter.Blur.NORMAL)
    val string = SpannableString(text)
    string.setSpan(MaskFilterSpan(blurMask), 0, string.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    setText(string)
}

fun EditText.hideKeyboard() {
    val imm = context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(this.windowToken, 0)
}

fun EditText.showKeyboard() {
    val imm = context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(this, 0)
}