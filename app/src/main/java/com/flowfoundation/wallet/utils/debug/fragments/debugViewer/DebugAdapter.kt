package com.flowfoundation.wallet.utils.debug.fragments.debugViewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.debug.DebugManager
import com.flowfoundation.wallet.utils.debug.ResourceUtility
import com.flowfoundation.wallet.utils.debug.setOnSingleClickListener
import com.flowfoundation.wallet.utils.debug.toast

class DebugAdapter(var list: List<DebugMessage>) :
    RecyclerView.Adapter<DebugMsgHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DebugMsgHolder {
        val inflater = LayoutInflater.from(parent.context)
        return DebugMsgHolder(inflater, parent)
    }

    override fun onBindViewHolder(holder: DebugMsgHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount() = list.size
}

class DebugMsgHolder(inflater: LayoutInflater, parent: ViewGroup) :
    RecyclerView.ViewHolder(inflater.inflate(R.layout.debug_log_item, parent, false)) {
    private var msgTitle: TextView? = null
    private var msgSubTitle: TextView? = null

    init {
        msgTitle = itemView.findViewById(R.id.log_msg)
        msgSubTitle = itemView.findViewById(R.id.log_sub_msg)
    }

    fun bind(debugMessage: DebugMessage) {
        itemView.setBackgroundColor(ResourceUtility.getColor(R.color.bg_1))
        msgTitle?.setTextColor(ResourceUtility.getColor(debugMessage.titleColor))
        msgSubTitle?.setTextColor(ResourceUtility.getColor(debugMessage.bodyColor))

        if (debugMessage.title.isNotEmpty()) {
            msgTitle?.text = debugMessage.title
            msgTitle?.visibility = View.VISIBLE
        } else {
            msgTitle?.visibility = View.GONE
        }
        if (debugMessage.body.isNotEmpty()) {
            msgSubTitle?.text = debugMessage.body
            msgSubTitle?.visibility = View.VISIBLE
        } else {
            msgSubTitle?.visibility = View.GONE
        }
        itemView.setOnSingleClickListener {
            debugMessage.collapsed = !debugMessage.collapsed
            toggleCollapse(debugMessage)
        }

        itemView.setOnLongClickListener {
            val clipboard: ClipboardManager? =
                DebugManager.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = ClipData.newPlainText(
                "OK Debug Message",
                debugMessage.title + "\n" + debugMessage.body
            )
            clipboard?.setPrimaryClip(clipData)
            DebugManager.applicationContext.toast(
                "Debug Message Copied to Clipboard",
                Toast.LENGTH_SHORT
            )
            false
        }

        toggleCollapse(debugMessage)
    }

    private fun toggleCollapse(debugMessage: DebugMessage) {
        msgSubTitle?.apply {
            if (debugMessage.collapsed) {
                maxLines = 0
            } else {
                maxLines = Int.MAX_VALUE
                setBackgroundColor(
                    ResourceUtility.getColor(R.color.bg_2)
                )
            }
        }
    }
}
