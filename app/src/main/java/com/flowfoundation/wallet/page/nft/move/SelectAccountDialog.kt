package com.flowfoundation.wallet.page.nft.move

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.databinding.DialogSelectAccountBinding
import com.flowfoundation.wallet.page.nft.move.adapter.SelectAccountAdapter
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class SelectAccountDialog : BottomSheetDialogFragment() {

    private var selectedAddress: String? = null
    private var addressList: List<String>? = null
    private var result: Continuation<String?>? = null

    private lateinit var binding: DialogSelectAccountBinding

    private val adapter by lazy {
        SelectAccountAdapter(selectedAddress ?: "") {
            result?.resume(it)
            dismiss()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogSelectAccountBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        result ?: return
        with(binding.recyclerView) {
            adapter = this@SelectAccountDialog.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                ColorDividerItemDecoration(Color.TRANSPARENT, 8.dp2px().toInt())
            )
        }
        addressList?.let { adapter.setNewDiffData(it) }
    }

    override fun onCancel(dialog: DialogInterface) {
        result?.resume(null)
    }

    suspend fun show(
        selectedAddress: String?,
        addressList: List<String>?,
        fragmentManager: FragmentManager,
    ) = suspendCoroutine { result ->
        this.selectedAddress = selectedAddress
        this.addressList = addressList
        this.result = result
        show(fragmentManager, "")
    }

    override fun onResume() {
        if (result == null) {
            dismiss()
        }
        super.onResume()
    }

}