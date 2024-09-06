package com.flowfoundation.wallet.utils.debug

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

fun View.setOnSingleClickListener(listener: (() -> Unit)?) {
    setOnClickListener {
        isEnabled = false
        listener?.invoke()
        postDelayed({
            isEnabled = true
        }, 500)
    }
}

fun Runnable.postDelay(delay: Long) {
    Handler(Looper.getMainLooper()).postDelayed(this, delay)
}

fun Int.toDp(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}

fun RecyclerView.persistState(action: () -> Unit) {
    val recyclerViewState = layoutManager?.onSaveInstanceState()
    action()
    layoutManager?.onRestoreInstanceState(recyclerViewState)
}

fun RecyclerView.reload(persistState: Boolean = false) {
    if (persistState) {
        persistState {
            adapter?.reload()
        }
    } else {
        adapter?.reload()
    }
}

fun ViewPager2.reload() {
    adapter?.reload()
}

@SuppressLint("NotifyDataSetChanged")
fun RecyclerView.Adapter<*>.reload() {
    notifyDataSetChanged()
}

fun Context.toast(@StringRes stringId: Int, length: Int) =
    Toast.makeText(this, stringId, length).show()

fun Context.toast(message: CharSequence?, length: Int) =
    Toast.makeText(this, message, length).show()

fun showDialog(
    title: String? = null,
    message: String? = null,
    positiveButton: String? = null,
    negativeButton: String? = null,
    items: List<String>? = null,
    customView: View? = null,
    actionCallback: ((which: Int) -> Unit)? = null
): AlertDialog {
    val alertBuilder = DebugManager.applicationContext.let { AlertDialog.Builder(it) }
    alertBuilder.let {
        title?.let { alertBuilder.setTitle(title) }
        message?.let { alertBuilder.setMessage(message) }
        customView?.let { alertBuilder.setView(customView) }
        items?.let {
            alertBuilder.setItems(items.toTypedArray()) { dialog, which ->
                dialog.dismiss()
                actionCallback?.invoke(which)
            }
        }
        positiveButton?.let {
            alertBuilder.setPositiveButton(positiveButton) { _, _ ->
                actionCallback?.invoke(AlertDialog.BUTTON_POSITIVE)
            }
        }
        negativeButton?.let {
            alertBuilder.setNegativeButton(negativeButton) { _, _ ->
                actionCallback?.invoke(AlertDialog.BUTTON_NEGATIVE)
            }
        }
        alertBuilder.setOnDismissListener {
            actionCallback?.invoke(AlertDialog.BUTTON_NEUTRAL)
        }
    }

    val alertDialog = alertBuilder.create()
    alertDialog.show()
    return alertDialog
}
