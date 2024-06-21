package com.flowfoundation.wallet.base.recyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

abstract class BaseAdapter<T>(
    private val diffCallback: DiffUtil.ItemCallback<T> = BaseDiffCallback()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val differ by lazy { AsyncListDiffer(this, diffCallback) }

    override fun getItemCount(): Int = differ.currentList.size

    fun setNewDiffData(newData: List<T>, commitCallback: (() -> Unit)? = null) {
        differ.submitList(newData.toMutableList()) {
            commitCallback?.invoke()
        }
    }

    fun getData(): List<T> = differ.currentList

    fun getItem(position: Int): T = getData()[position]

    fun ViewGroup.inflate(@LayoutRes layoutId: Int): View {
        return LayoutInflater.from(this.context).inflate(layoutId, this, false)
    }
}

private class BaseDiffCallback<T> : DiffUtil.ItemCallback<T>() {

    override fun areItemsTheSame(oldItem: T & Any, newItem: T & Any): Boolean {
        return false
    }

    override fun areContentsTheSame(oldItem: T & Any, newItem: T & Any): Boolean {
        return false
    }
}