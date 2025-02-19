package com.flowfoundation.wallet.page.transaction.record

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentTransactionRecordBinding
import com.flowfoundation.wallet.page.transaction.record.presenter.TransactionRecordFragmentPresenter

class TransactionRecordFragment : Fragment() {
    private var _binding: FragmentTransactionRecordBinding? = null
    private val binding get() = _binding!!

    private lateinit var presenter: TransactionRecordFragmentPresenter
    private lateinit var viewModel: TransactionRecordViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter = TransactionRecordFragmentPresenter(binding, requireActivity())

        viewModel = ViewModelProvider(this)[TransactionRecordViewModel::class.java].apply {
            transferCountLiveData.observe(viewLifecycleOwner) { presenter.bind(it ?: 0) }
            transferListLiveData.observe(viewLifecycleOwner) {
                presenter.setListData(it)
            }
            load()
        }

        binding.refreshLayout.setOnRefreshListener { viewModel.load() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
