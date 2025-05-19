package com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.widgets

import android.annotation.SuppressLint
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.base.recyclerview.getItemView
import com.flowfoundation.wallet.databinding.ItemKeyboardT9KeyBinding
import com.flowfoundation.wallet.page.walletcreate.fragments.pincode.pin.KeyboardItem
import com.flowfoundation.wallet.utils.Env
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration

internal class T9Keyboard(
    private val container: LinearLayout,
    lineKeys: List<List<KeyboardItem>>,
    private val onKeyboardActionListener: ((key: KeyboardItem) -> Unit)? = null,
) : KeyboardLayout(container) {

    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: Adapter

    private val keys = lineKeys.flatten()

    override fun setup() {
        if (container.childCount > 0) {
            return
        }
        container.setBackgroundResource(R.color.background)

        recyclerView = LayoutInflater.from(context).inflate(R.layout.widget_keyboard_t9, container, false) as RecyclerView
        container.addView(recyclerView)
        adapter = Adapter()
        with(recyclerView) {
            this.adapter = this@T9Keyboard.adapter
            layoutManager = GridLayoutManager(context, 3)
            val hPadding = 15.dp2px().toDouble()
            addItemDecoration(GridSpaceItemDecoration(start = hPadding, end = hPadding, horizontal = hPadding, vertical = 2.dp2px().toDouble()))
        }
        adapter.setNewDiffData(keys.toMutableList())
    }

    private inner class Adapter : BaseAdapter<KeyboardItem>() {
        inner class ViewHolder(val view: View) : BaseViewHolder(view) {
            fun bind(item: KeyboardItem) {
                with(ItemKeyboardT9KeyBinding.bind(view)) {
                    view.isEnabled = item.type != KeyboardItem.TYPE_EMPTY_KEY
                    view.setOnClickListener {
                        onKeyboardActionListener?.invoke(item)
                    }
                    view.setOnLongClickListener(null)
                    if (item.type == KeyboardItem.TYPE_DELETE_KEY) {
                        view.setOnLongClickListener {
                            onKeyboardActionListener?.invoke(KeyboardItem(type = KeyboardItem.TYPE_CLEAR_KEY))
                            true
                        }
                    }
                    when (item.type) {
                        KeyboardItem.TYPE_TEXT_KEY -> {
                            numberText.text = "${item.number}"
                            abcText.text = item.charText
                            numberText.setVisible(true)
                            abcText.setVisible(true)
                            actionIcon.setVisible(false)
                        }
                        KeyboardItem.TYPE_DELETE_KEY -> {
                            actionIcon.setImageResource(item.actionIcon)
                            numberText.setVisible(false, invisible = true)
                            abcText.setVisible(false, invisible = true)
                            actionIcon.setVisible(true)
                        }
                        else -> {
                            numberText.setVisible(false, invisible = true)
                            abcText.setVisible(false, invisible = true)
                            actionIcon.setVisible(false)
                        }
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return ViewHolder(parent.getItemView(R.layout.item_keyboard_t9_key))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val data = getItem(position)
            (holder as ViewHolder).bind(data)
        }
    }
}

@SuppressLint("MissingPermission")
fun vibrateKeyboard() {
    val vibrator = Env.getApp().getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
    val pattern = longArrayOf(1, 50, 1)
    vibrator.vibrate(pattern, -1)
}