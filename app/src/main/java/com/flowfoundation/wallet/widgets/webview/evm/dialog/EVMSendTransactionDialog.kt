package com.flowfoundation.wallet.widgets.webview.evm.dialog

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogEvmTransactionBinding
import com.flowfoundation.wallet.manager.config.isGasFree
import com.flowfoundation.wallet.network.ApiService
import com.flowfoundation.wallet.network.model.TransactionDecodeParams
import com.flowfoundation.wallet.network.retrofitApi
import com.flowfoundation.wallet.page.browser.loadFavicon
import com.flowfoundation.wallet.page.browser.toFavIcon
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.utils.uiScope
import com.flowfoundation.wallet.widgets.webview.evm.model.EVMTransactionDialogModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.web3j.utils.Convert
import org.web3j.utils.Numeric


class EVMSendTransactionDialog: BottomSheetDialogFragment() {

    private val data by lazy { arguments?.getParcelable<EVMTransactionDialogModel>(EXTRA_DATA) }

    private lateinit var binding: DialogEvmTransactionBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogEvmTransactionBinding.inflate(inflater)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (data == null) {
            dismiss()
            return
        }
        val data = data ?: return
        loadTransactionDecodeData(data)
        with(binding) {
            title1.text = R.string.transaction_to.res2String()
            iconView.loadFavicon(data.logo ?: data.url?.toFavIcon())
            nameView.text = data.title
            ivClose.setOnClickListener {
                approveCallback?.invoke(false)
                dismiss()
            }
            actionButton.setOnProcessing {
                approveCallback?.invoke(true)
                dismiss()
            }
            ioScope {
                if (isGasFree()) {
                    uiScope {
                        tvFeeFree.text = "0.001"
                        tvFeeFree.paintFlags = tvFeeFree.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        tvFeeFree.visible()
                        tvFee.text = "0.00"
                    }
                } else {
                    uiScope {
                        tvFeeFree.gone()
                        tvFee.text = "0.001"
                    }
                }
            }
            tvAddress.text = data.toAddress
            val amountValue = Numeric.decodeQuantity(data.value ?: "0")
            val value = Convert.fromWei(amountValue.toString(), Convert.Unit.ETHER)
            tvAmount.text = "$value FLOW"
            tvCallData.text = data.data
            ivCopy.setOnClickListener {
                textToClipboard(data.data.orEmpty())
                toast(msgRes = R.string.copied_to_clipboard)
            }
        }
    }

    private fun loadTransactionDecodeData(data: EVMTransactionDialogModel) {
        ioScope {
            try {
                val service = retrofitApi().create(ApiService::class.java)
                val response = service.getEVMTransactionDecodeData(
                    TransactionDecodeParams(
                        to = data.toAddress ?: "",
                        data = data.data ?: ""
                    )
                )
                uiScope {
                    with(binding) {
                        tvContact.text = response.name
                        ivVerified.setVisible(response.isVerified)
                        response.decodedData?.let { decodedData ->
                            if (decodedData.isFunctionFormat()) {
                                tvFunction.text = decodedData.name
                                decodedData.params?.let { paramsList ->
                                    llParameters.removeAllViews()
                                    paramsList.forEach { param ->
                                        val paramValue =
                                            param.getStringListValue() ?: param.getStringValue()
                                        if (paramValue is List<*>) {
                                            llParameters.addView(
                                                TransactionParameterView(requireContext()).apply {
                                                    setData(
                                                        param.name,
                                                        paramValue.first().toString()
                                                    )
                                                }
                                            )
                                        } else {
                                            llParameters.addView(
                                                TransactionParameterView(requireContext()).apply {
                                                    setData(param.name, paramValue.toString())
                                                }
                                            )
                                        }
                                    }
                                    clParameters.visible()
                                    clPossibility.visible()
                                } ?: run {
                                    clParameters.gone()
                                    clPossibility.visible()
                                }
                            } else {
                                decodedData.allPossibilities.firstOrNull()?.let {
                                    tvFunction.text = it.function
                                    if (it.isStringListParams()) {
                                        val stringParams = it.getStringParams()
                                        llParameters.removeAllViews()
                                        stringParams?.forEach { value ->
                                            llParameters.addView(
                                                TransactionParameterView(requireContext()).apply {
                                                    setData(value)
                                                }
                                            )
                                        }
                                        clParameters.visible()
                                    } else if (it.isMapParams()) {
                                        val mapParams = it.getMapParams()
                                        llParameters.removeAllViews()
                                        mapParams?.forEach { (key, value) ->
                                            llParameters.addView(
                                                TransactionParameterView(requireContext()).apply {
                                                    setData(key, value)
                                                }
                                            )
                                        }
                                        clParameters.visible()
                                    } else {
                                        clParameters.gone()
                                    }
                                    clPossibility.visible()
                                } ?: run {
                                    clPossibility.gone()
                                }
                            }
                        } ?: run {
                            clPossibility.gone()
                        }
                        cvContactInfo.visible()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uiScope {
                    binding.cvContactInfo.gone()
                }
            }
        }
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
            this.approveCallback = callback
        }

        fun show(
            fragmentManager: FragmentManager,
            data: EVMTransactionDialogModel,
        ) {
            EVMSendTransactionDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_DATA, data)
                }
            }.show(fragmentManager, "")
        }
    }
}