package com.flowfoundation.wallet.utils.extensions

import android.graphics.BlurMaskFilter
import android.graphics.MaskFilter
import android.text.InputFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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

fun EditText.setDecimalDigitsFilter(decimal: Int) {
    val decimalDigitsFilter = InputFilter { source, start, end, dest, dstart, dend ->
        val builder = StringBuilder(dest)
        builder.replace(dstart, dend, source.subSequence(start, end).toString())
        val result = builder.toString()

        if (result.isEmpty() || result == ".") {
            return@InputFilter null
        }

        if (!result.matches(Regex("^\\d*\\.?\\d*$"))) {
            return@InputFilter ""
        }

        val dotIndex = result.indexOf('.')
        if (dotIndex != -1) {
            val decimalLength = result.length - dotIndex - 1
            if (decimalLength > decimal) {
                val targetLength = dotIndex + 1 + decimal
                if (dstart < targetLength) {
                    return@InputFilter source.subSequence(
                        start,
                        end - (result.length - targetLength)
                    )
                }
                return@InputFilter ""
            }
        }

        null
    }
    filters = arrayOf(decimalDigitsFilter)
}

fun SpannableString.setSafeSpan(
    target: String,
    span: Any,
    flags: Int = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
): SpannableString {
    val index = indexOf(target)
    if (index != -1 && index + target.length <= length) {
        setSpan(span, index, index + target.length, flags)
    }
    return this
}

fun TextView.setSpannableText(
    text: String,
    target: String,
    color: Int
) {
    val spannableString = SpannableString(text).setSafeSpan(
        target,
        ForegroundColorSpan(color)
    )
    this.text = spannableString
}