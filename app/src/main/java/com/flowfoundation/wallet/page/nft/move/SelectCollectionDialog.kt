package com.flowfoundation.wallet.page.nft.move

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.transition.Scene
import android.transition.Slide
import android.transition.TransitionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DialogSelectCollectionBinding
import com.flowfoundation.wallet.page.nft.move.adapter.SelectCollectionAdapter
import com.flowfoundation.wallet.page.nft.move.model.CollectionDetailInfo
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.extensions.hideKeyboard
import com.flowfoundation.wallet.utils.extensions.isVisible
import com.flowfoundation.wallet.utils.extensions.setVisible
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class SelectCollectionDialog: BottomSheetDialogFragment() {

    private var selectedCollectionId: String? = null
    private lateinit var binding: DialogSelectCollectionBinding
    private var result: Continuation<CollectionDetailInfo?>? = null
    private val viewModel by lazy { ViewModelProvider(this)[SelectCollectionViewModel::class.java] }
    private val adapter by lazy {
        SelectCollectionAdapter(
            selectedCollectionId
        ) {
            result?.resume(it)
            dismiss()
        }
    }

    private var fromAddress: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogSelectCollectionBinding.inflate(inflater)
        return binding.rootView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface: DialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            setupFullHeight(bottomSheetDialog)
        }
        return dialog
    }

    private fun setupFullHeight(bottomSheetDialog: BottomSheetDialog) {
        val bottomSheet =
            bottomSheetDialog.findViewById<ViewGroup>(R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            bottomSheet.requestLayout()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.ivClose.setOnClickListener { dismissAllowingStateLoss() }
        binding.cancelButton.setOnClickListener {
            onSearchFocusChange(false)
            binding.editText.hideKeyboard()
            binding.editText.setText("")
            binding.editText.clearFocus()
            viewModel.clearSearch()
        }
        with(binding.rvCollectionList) {
            adapter = this@SelectCollectionDialog.adapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            addItemDecoration(
                ColorDividerItemDecoration(Color.TRANSPARENT, 8.dp2px().toInt())
            )
        }

        with(binding.editText) {
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard()
                    viewModel.search(text.toString().trim())
                    clearFocus()
                }
                return@setOnEditorActionListener false
            }
            doOnTextChanged { text, _, _, _ ->
                viewModel.search(text.toString().trim())
            }
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus -> onSearchFocusChange(hasFocus) }
        }

        with(viewModel) {
            collectionListLiveData.observe(this@SelectCollectionDialog) { list ->
                adapter.setNewDiffData(list)
            }
            // Use the fromAddress to load collections; adjust loadCollections() in your ViewModel accordingly
            fromAddress?.let {
                loadCollections(it)
            } ?: loadCollections()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        result?.resume(null)
    }

    override fun onResume() {
        if (result == null) {
            dismiss()
        }
        super.onResume()
    }

    private fun onSearchFocusChange(hasFocus: Boolean) {
        val isVisible = hasFocus || !binding.editText.text.isNullOrBlank()
        val isVisibleChange = isVisible != binding.cancelButton.isVisible()

        if (isVisibleChange) {
            TransitionManager.go(Scene(binding.root as ViewGroup), Slide(Gravity.END).apply { duration = 150 })
            binding.cancelButton.setVisible(isVisible)
        }
    }

    suspend fun show(
        selectedCollectionId: String?,
        fromAddress: String,
        fragmentManager: FragmentManager
    ) = suspendCoroutine { cont ->
        this.selectedCollectionId = selectedCollectionId
        this.fromAddress = fromAddress
        this.result = cont
        show(fragmentManager, "")
    }
}