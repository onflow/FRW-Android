package com.flowfoundation.wallet.utils.debug.fragments.debugTweaks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentDebugTweaksBinding
import com.flowfoundation.wallet.databinding.MenuHeaderBinding
import com.flowfoundation.wallet.databinding.MenuItemBinding
import com.flowfoundation.wallet.utils.debug.DebugManager
import com.flowfoundation.wallet.utils.debug.DebugManager.toggleDebugTweaks
import com.flowfoundation.wallet.utils.debug.DebugTweak
import com.flowfoundation.wallet.utils.debug.setOnSingleClickListener
import okhttp3.internal.toImmutableList
import okhttp3.internal.toImmutableMap

@Suppress("PrivatePropertyName")
class DebugTweaksFragment : Fragment() {

    private lateinit var binding: FragmentDebugTweaksBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDebugTweaksBinding.inflate(inflater, container, false)
        setCloseButton()
        setupRecyclerView()
        return binding.root
    }

    private fun setCloseButton() {
        binding.headerClose.setOnSingleClickListener {
            toggleDebugTweaks()
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.adapter = DebugTweakAdapter()
    }

    private class DebugTweakAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val ITEM_VIEW_TYPE_HEADER = -1
        private val ITEM_VIEW_TYPE_ITEM = -2

        private val groupedTweaks: Map<String, List<DebugTweak<Any>>>
            get() {
                val grouped: MutableMap<String, List<DebugTweak<Any>>> = mutableMapOf()
                DebugManager.tweaks.filter { tweak -> tweak.isActive }.forEach { tweak ->
                    val key = tweak.category
                    grouped[key]?.toMutableList()?.let {
                        it.add(tweak)
                        grouped[key] = it.toImmutableList()
                    } ?: run {
                        grouped[key] = listOf(tweak)
                    }
                }
                return grouped.toImmutableMap()
            }

        override fun getItemCount(): Int {
            return groupedTweaks.count() + DebugManager.tweaks.count { it.isActive }
        }

        private fun getItem(position: Int): Any? {
            var index = 0
            for (entry in groupedTweaks) {
                if (index == position) {
                    return entry.key
                }
                index++
                for (tweak in entry.value) {
                    if (index == position) {
                        return tweak
                    }
                    index++
                }
            }
            return null
        }

        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is String -> ITEM_VIEW_TYPE_HEADER
                is DebugTweak<*> -> ITEM_VIEW_TYPE_ITEM
                else -> throw ClassCastException("Unknown viewType for $position")
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                ITEM_VIEW_TYPE_HEADER -> {
                    val viewBinding: MenuHeaderBinding = DataBindingUtil.inflate(
                        layoutInflater, R.layout.menu_header, parent, false
                    )
                    HeaderViewHolder(viewBinding)
                }
                ITEM_VIEW_TYPE_ITEM -> {
                    val viewBinding: MenuItemBinding = DataBindingUtil.inflate(
                        layoutInflater, R.layout.menu_item, parent, false
                    )
                    ItemViewHolder(viewBinding)
                }
                else -> {
                    throw ClassCastException("Unknown viewType $viewType")
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is HeaderViewHolder -> {
                    (getItem(position) as? String)?.let { holder.bind(it) }
                }
                is ItemViewHolder -> {
                    (getItem(position) as? DebugTweak<*>)?.let { holder.bind(it) }
                }
            }
        }

        class HeaderViewHolder(
            private val headerBinding: MenuHeaderBinding
        ) : RecyclerView.ViewHolder(headerBinding.root) {
            fun bind(header: String) {
                headerBinding.menuHeaderText.text = header
            }
        }

        class ItemViewHolder(
            private val itemBinding: MenuItemBinding
        ) : RecyclerView.ViewHolder(itemBinding.root) {
            fun bind(tweak: DebugTweak<*>) {
                itemBinding.titleLabel.text = tweak.name
                if (tweak.value is Boolean) {
                    itemBinding.arrowIcon.visibility = View.GONE
                    itemBinding.toggleSwitch.visibility = View.VISIBLE
                    tweak.boolValue?.let { itemBinding.toggleSwitch.isChecked = it }
                    itemBinding.toggleSwitch.setOnSingleClickListener {
                        (tweak as? DebugTweak<Boolean>)?.value = itemBinding.toggleSwitch.isChecked
                        tweak.postAction?.invoke()
                        if (tweak.restartAppAndLogout) {
                            DebugManager.terminateApp(true)
                        } else if (tweak.restartApp) {
                            DebugManager.terminateApp()
                        }
                    }
                } else {
                    itemBinding.arrowIcon.visibility = View.VISIBLE
                    itemBinding.toggleSwitch.visibility = View.GONE
                    itemBinding.menuItem.setOnSingleClickListener {
                        tweak.performAction {
                            if (tweak.restartAppAndLogout) {
                                DebugManager.terminateApp(true)
                            } else if (tweak.restartApp) {
                                DebugManager.terminateApp()
                            }
                        }
                    }
                }
            }
        }
    }
}
