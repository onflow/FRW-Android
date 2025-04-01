package com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.widgets

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.core.view.children
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.KeyboardItem
import com.flowfoundation.wallet.utils.extensions.res2color

class PinCodeInputLayout : LinearLayout {

    private val selectedColor by lazy { ColorStateList.valueOf(R.color.colorSecondary.res2color()) }
    private val unselectedColor by lazy { ColorStateList.valueOf(R.color.border_3.res2color()) }

    private val keys = mutableListOf<KeyboardItem>()

    private val checkKeys = mutableListOf<KeyboardItem>()

    private var isChecking = false

    private var checkCallback: ((isSuccess: Boolean) -> Unit)? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
      : super(context, attrs, defStyleAttr)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        LayoutInflater.from(context).inflate(R.layout.widget_pin_code_input, this)
    }

    fun setCheckCallback(callback: (isSuccess: Boolean) -> Unit) {
        this.checkCallback = callback
    }

    fun checking() {
        isChecking = true
        clear()
    }

    fun input(key: KeyboardItem) {
        val child = children.firstOrNull { !it.isSelected } ?: return
        child.isSelected = true
        child.backgroundTintList = selectedColor
        if (isChecking) {
            checkKeys.add(key)
            if (checkKeys.size == keys.size) {
                val isSameKey = isSameKey()
                checkCallback?.invoke(isSameKey)
                if (!isSameKey) {
                    shakeAndClear()
                }
            }
        } else {
            keys.add(key)
        }
    }

    fun delete() {
        val child = children.lastOrNull { it.isSelected } ?: return
        child.isSelected = false
        child.backgroundTintList = unselectedColor
        if (isChecking) {
            checkKeys.removeLast()
        } else {
            keys.removeLast()
        }
    }

    fun keys() = keys

    fun isChecking() = isChecking

    private fun isSameKey(): Boolean {
        return keys.map { it.number }.toList() == checkKeys.map { it.number }.toList()
    }

    fun clear(keysCLear: Boolean = false) {
        if (keysCLear) keys.clear()

        checkKeys.clear()
        children.forEach {
            it.isSelected = false
            it.backgroundTintList = unselectedColor
        }
    }

    fun shakeAndClear(keysCLear: Boolean = false) {
        vibrateKeyboard()
        val animation = AnimationUtils.loadAnimation(context, R.anim.pin_code_shake)
        children.forEach { child ->
            child.startAnimation(animation)
        }
        postDelayed({ clear(keysCLear) }, 800)
    }
}