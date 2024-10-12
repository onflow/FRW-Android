package com.flowfoundation.wallet.widgets.webview.evm.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.fragment.app.FragmentManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogEvmSignTypedDataBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.manager.evm.COALinkCheckManager
import com.flowfoundation.wallet.page.browser.loadFavicon
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.utils.ScreenUtils
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.shortenEVMString
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMTypedMessage
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject


class EVMSignTypedDataDialog : BottomSheetDialogFragment() {

    private val data by lazy { arguments?.getParcelable<FclDialogModel>(EXTRA_DATA) }

    private lateinit var binding: DialogEvmSignTypedDataBinding

    private var isFullHeight = false
    private val height by lazy {
        ScreenUtils.getScreenHeight() / 2
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogEvmSignTypedDataBinding.inflate(inflater)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)

                it.layoutParams.height = height
                behavior.peekHeight = height
                behavior.isFitToContents = false
                behavior.isHideable = false

                behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        when (newState) {
                            BottomSheetBehavior.STATE_DRAGGING,
                            BottomSheetBehavior.STATE_EXPANDED -> {
                                if (isFullHeight.not()) {
                                    it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                                    isFullHeight = true
                                    it.requestLayout()
                                }
                            }

                            BottomSheetBehavior.STATE_COLLAPSED,
                            BottomSheetBehavior.STATE_HIDDEN -> {
                                if (isFullHeight) {
                                    it.layoutParams.height = height
                                    isFullHeight = false
                                    it.requestLayout()
                                }
                            }

                            else -> {}
                        }
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    }
                })
            }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (data == null) {
            dismiss()
            return
        }
        val data = data ?: return
        with(binding) {
            title1.text = R.string.message_to.res2String()
            iconView.loadFavicon(data.logo ?: data.url?.toFavIcon())
            nameView.text = data.title
            try {
                val typedMessage = Gson().fromJson(data.signMessage, EVMTypedMessage::class.java)
                if (typedMessage == null) {
                    dismissMessageLayout()
                } else {
                    binding.svTypedMessage.visible()
                    if (typedMessage.primaryType.isNullOrEmpty()) {
                        llMessage.gone()
                    } else {
                        llMessage.visible()
                        tvMessageKey.text = R.string.sign_message_primary_type.res2String()
                        tvMessageValue.text = typedMessage.primaryType
                    }
                    if (typedMessage.message.isEmpty) {
                        llData.gone()
                    } else {
                        llData.visible()
                        llData.removeAllViews()
                        typedMessage.message.entrySet().forEach { entry ->
                            val itemView = LayoutInflater.from(root.context)
                                .inflate(R.layout.item_evm_sign_typed_data, llData, false)
                            (itemView as ViewGroup).setupData(entry.key, entry.value)
                            llData.addView(itemView)
                        }
                    }
                }
            } catch (e: Exception) {
                dismissMessageLayout()
            }
            ivClose.setOnClickListener {
                approveCallback?.invoke(false)
                dismiss()
            }
            actionButton.setOnProcessing {
                ioScope {
                    val isLinked = COALinkCheckManager.checkCOALink()
                    if (isLinked) {
                        approveCallback?.invoke(true)
                        dismiss()
                    } else {
                        COALinkCheckManager.createCOALink { isSuccess ->
                            if (isSuccess) {
                                approveCallback?.invoke(true)
                                dismiss()
                            } else {
                                approveCallback?.invoke(false)
                                dismiss()
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun ViewGroup.setupData(key: String, value: JsonElement) {
        val tvDataKey = findViewById<TextView>(R.id.tv_data_key)
        val llDataValue = findViewById<LinearLayoutCompat>(R.id.ll_data_value)
        val tvDataValue = findViewById<TextView>(R.id.tv_data_value)
        tvDataKey.text = key.replaceFirstChar { it.uppercase() } + ":"

        if (value.isJsonObject) {
            llDataValue.visible()
            llDataValue.removeAllViews()
            (value as JsonObject).entrySet().forEach { entry ->
                val childView = LayoutInflater.from(this.context)
                    .inflate(R.layout.item_evm_sign_typed_message, llDataValue, false)
                childView.setupMessage(entry.key, entry.value)
                llDataValue.addView(childView)
            }
        } else if (value.isJsonArray) {
            llDataValue.visible()
            llDataValue.removeAllViews()
            (value as JsonArray).forEach { item ->
                val childView = LayoutInflater.from(this.context)
                    .inflate(R.layout.item_evm_sign_typed_array_data, llDataValue, false)
                childView.setupArrayData(item)
                llDataValue.addView(childView)
            }
        } else {
            tvDataValue.visible()
            tvDataValue.text = shortenEVMString(
                if (value.isJsonPrimitive && (value.asJsonPrimitive).isString) {
                    value.asString
                } else {
                    value.toString()
                })
        }
    }

    private fun View.setupArrayData(value: JsonElement) {
        val llArrayData = findViewById<LinearLayoutCompat>(R.id.ll_array_data)
        val tvArrayData = findViewById<TextView>(R.id.tv_array_data)
        if (value.isJsonObject) {
            llArrayData.visible()
            llArrayData.removeAllViews()
            (value as JsonObject).entrySet().forEach { entry ->
                val childView = LayoutInflater.from(this.context)
                    .inflate(R.layout.item_evm_sign_typed_array_message, llArrayData, false)
                childView.setupArrayMessage(entry.key, entry.value)
                llArrayData.addView(childView)
            }
        } else {
            tvArrayData.visible()
            tvArrayData.text = shortenEVMString(
                if (value.isJsonPrimitive && (value.asJsonPrimitive).isString) {
                    value.asString
                } else {
                    value.toString()
                })
        }
    }

    private fun View.setupArrayMessage(key: String, value: JsonElement) {
        val tvMessageKey = findViewById<TextView>(R.id.tv_array_message_key)
        val tvMessageValue = findViewById<TextView>(R.id.tv_array_message_value)
        tvMessageKey.text = key.replaceFirstChar { it.uppercase() }
        tvMessageValue.text = shortenEVMString(
            if (value.isJsonPrimitive && (value.asJsonPrimitive).isString) {
                value.asString
            } else {
                value.toString()
            }
        )
    }

    private fun View.setupMessage(key: String, value: JsonElement) {
        val tvMessageKey = findViewById<TextView>(R.id.tv_message_key)
        val tvMessageValue = findViewById<TextView>(R.id.tv_message_value)
        tvMessageKey.text = key.replaceFirstChar { it.uppercase() }
        tvMessageValue.text = shortenEVMString(
            if (value.isJsonPrimitive && (value.asJsonPrimitive).isString) {
                value.asString
            } else {
                value.toString()
            }
        )
    }

    private fun dismissMessageLayout() {
        binding.svTypedMessage.gone()
    }

    override fun onCancel(dialog: DialogInterface) {
        approveCallback?.invoke(false)
    }

    override fun onDestroy() {
        approveCallback = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_DATA = "data"

        private var approveCallback: ((isApprove: Boolean) -> Unit)? = null

        fun observe(callback: (isApprove: Boolean) -> Unit) {
            approveCallback = callback
        }

        fun show(
            fragmentManager: FragmentManager,
            data: FclDialogModel,
        ) {
            EVMSignTypedDataDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_DATA, data)
                }
            }.show(fragmentManager, "")
        }
    }
}