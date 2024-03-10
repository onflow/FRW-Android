package com.flowfoundation.wallet.page.nft.collectionlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.databinding.DialogAddCollectionConfirmBinding
import com.flowfoundation.wallet.manager.config.NftCollection
import com.flowfoundation.wallet.utils.uiScope
import jp.wasabeef.glide.transformations.BlurTransformation

class NftEnableConfirmDialog : BottomSheetDialogFragment() {

    private val collection by lazy { arguments?.getParcelable<NftCollection>(EXTRA_TOKEN)!! }

    private lateinit var binding: DialogAddCollectionConfirmBinding

    private lateinit var viewModel: NftCollectionListViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogAddCollectionConfirmBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity())[NftCollectionListViewModel::class.java].apply {
            cadenceExecuteLiveData.observe(this@NftEnableConfirmDialog) {
                if (it) {
                    dismiss()
                    cadenceExecuteLiveData.value = false
                }
            }
        }

        binding.closeButton.setOnClickListener { dismiss() }
        with(binding) {
            nameView.text = collection.name
            descView.text = collection.description
            Glide.with(coverView).load(collection.banner).transform(BlurTransformation(2, 5)).into(coverView)
            actionButton.setOnClickListener {
                uiScope {
                    actionButton.setProgressVisible(true)
                    viewModel.addToken(collection)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TOKEN = "extra_token"

        fun show(fragmentManager: FragmentManager, collection: NftCollection) {
            NftEnableConfirmDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_TOKEN, collection)
                }
            }.show(fragmentManager, "")
        }
    }
}