package com.flowfoundation.wallet.page.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.journeyapps.barcodescanner.ScanOptions
import com.flowfoundation.wallet.databinding.FragmentExploreBinding
import com.flowfoundation.wallet.page.explore.model.ExploreModel
import com.flowfoundation.wallet.page.explore.presenter.ExplorePresenter
import com.flowfoundation.wallet.page.scan.dispatchScanResult
import com.flowfoundation.wallet.utils.launch
import com.flowfoundation.wallet.utils.registerBarcodeLauncher

class ExploreFragment : Fragment() {

    private lateinit var binding: FragmentExploreBinding
    private lateinit var presenter: ExplorePresenter
    private lateinit var viewModel: ExploreViewModel

    private lateinit var barcodeLauncher: ActivityResultLauncher<ScanOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        barcodeLauncher = registerBarcodeLauncher { result -> dispatchScanResult(requireContext(), result.orEmpty()) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentExploreBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.searchBox.scanButton.setOnClickListener {
            barcodeLauncher.launch()
        }
        presenter = ExplorePresenter(this, binding)
        viewModel = ViewModelProvider(requireActivity())[ExploreViewModel::class.java].apply {
            bindActivity(requireActivity())
            recentLiveData.observe(viewLifecycleOwner) { presenter.bind(ExploreModel()) }
            bookmarkLiveData.observe(viewLifecycleOwner) { presenter.bind(ExploreModel()) }
            dAppsLiveData.observe(viewLifecycleOwner) { presenter.bind(ExploreModel(dAppList = it)) }
            dAppTagsLiveData.observe(viewLifecycleOwner) { presenter.bind(ExploreModel(dAppTagList = it)) }
            load()
        }
    }
}