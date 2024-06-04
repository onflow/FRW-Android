package com.flowfoundation.wallet.page.nft.nftlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentNftBinding
import com.flowfoundation.wallet.manager.wallet.WalletManager
import com.flowfoundation.wallet.page.nft.nftlist.model.NFTFragmentModel
import com.flowfoundation.wallet.page.nft.nftlist.presenter.NFTFragmentPresenter
import com.flowfoundation.wallet.page.nft.nftlist.presenter.NftEmptyPresenter
import com.flowfoundation.wallet.utils.stopShimmer

class NFTFragment : Fragment() {

    private lateinit var binding: FragmentNftBinding

    private lateinit var presenter: NFTFragmentPresenter
    private lateinit var emptyPresenter: NftEmptyPresenter

    private lateinit var viewModel: NftViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNftBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter = NFTFragmentPresenter(this, binding)
        emptyPresenter = NftEmptyPresenter(binding.emptyContainer)

        viewModel = ViewModelProvider(requireActivity())[NftViewModel::class.java].apply {
            listNftLiveData.observe(viewLifecycleOwner) { presenter.bind(NFTFragmentModel(listPageData = it)) }
            gridNftLiveData.observe(viewLifecycleOwner) { presenter.bind(NFTFragmentModel(listPageData = it)) }

            emptyLiveData.observe(viewLifecycleOwner) { isEmpty ->
                emptyPresenter.setVisible(isEmpty)
                stopShimmer(binding.shimmerLayout.shimmerLayout)
            }
            listScrollChangeLiveData.observe(viewLifecycleOwner) { presenter.bind(NFTFragmentModel(onListScrollChange = it)) }
            favoriteLiveData.observe(viewLifecycleOwner) { presenter.bind(NFTFragmentModel(favorite = it)) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (WalletManager.isEVMAccountSelected()) {
            viewModel.requestEVMList()
        } else {
            viewModel.requestList()
            viewModel.requestGrid()
        }
    }
}