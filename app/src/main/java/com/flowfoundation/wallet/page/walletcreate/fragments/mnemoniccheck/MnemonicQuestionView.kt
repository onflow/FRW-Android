package com.flowfoundation.wallet.page.walletcreate.fragments.mnemoniccheck

import android.content.Context
import android.content.res.ColorStateList
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.children
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.res2color
import com.flowfoundation.wallet.widgets.CustomTypefaceSpan

class MnemonicQuestionView : FrameLayout {

    private val titleView by lazy { findViewById<TextView>(R.id.title_view) }
    private val button1 by lazy { findViewById<MaterialButton>(R.id.button1) }
    private val button2 by lazy { findViewById<MaterialButton>(R.id.button2) }
    private val button3 by lazy { findViewById<MaterialButton>(R.id.button3) }
    private val toggleGroup by lazy { findViewById<MaterialButtonToggleGroup>(R.id.toggleButton) }

    private val successColor by lazy { ColorStateList.valueOf(R.color.success.res2color()) }
    private val normalColor by lazy { ColorStateList.valueOf(R.color.border_2.res2color()) }
    private val errorColor by lazy { ColorStateList.valueOf(R.color.success.res2color()) }

    private lateinit var question: MnemonicQuestionModel

    private val indexFont by lazy { resources.getFont(R.font.inter_semi_bold) }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
      : super(context, attrs, defStyleAttr)

    private var onButtonCheckedListener: ((isVerified: Boolean) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.item_mnemonic_check_toggle, this)
        toggleGroup.addOnButtonCheckedListener { _, _, _ -> updateState() }
    }

    fun setOnButtonCheckedListener(onButtonCheckedListener: (isVerified: Boolean) -> Unit) {
        this.onButtonCheckedListener = onButtonCheckedListener
    }

    private fun updateState() {
        toggleGroup.children.forEach {
            val button = it as MaterialButton
            if (button.isChecked) {
                val color = if (button.text == question.mnemonic) successColor else errorColor
                button.setTextColor(color)
                button.strokeColor = color
            } else {
                button.setTextColor(R.color.text.res2color())
                button.strokeColor = normalColor
            }
        }
        onButtonCheckedListener?.invoke(isVerified())
    }

    fun bindData(question: MnemonicQuestionModel) {
        this.question = question
        val index = "#${question.index + 1}"
        val title = SpannableStringBuilder(context.getString(R.string.mnemonic_question_title, index))
        title.setSpan(CustomTypefaceSpan(indexFont), title.indexOf(index), title.indexOf(index) + index.length, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        titleView.text = title
        button1.text = question.question[0]
        button2.text = question.question[1]
        button3.text = question.question[2]
    }

    fun isVerified(): Boolean {
        toggleGroup.children.forEach {
            val button = it as MaterialButton
            if (button.isChecked) {
                return button.text == question.mnemonic
            }
        }
        return false
    }

    fun isChecked(): Boolean {
        toggleGroup.children.forEach {
            val button = it as MaterialButton
            if (button.isChecked) {
                return true
            }
        }
        return false
    }
}