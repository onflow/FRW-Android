package com.flowfoundation.wallet.page.address

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentAddressBookBinding
import com.flowfoundation.wallet.page.address.model.AddressBookFragmentModel
import com.flowfoundation.wallet.page.address.presenter.AddressBookFragmentPresenter
import com.flowfoundation.wallet.widgets.ProgressDialog

class AddressBookFragment : Fragment() {
    private lateinit var binding: FragmentAddressBookBinding
    private lateinit var viewModel: AddressBookViewModel
    private lateinit var presenter: AddressBookFragmentPresenter

    private val progressDialog by lazy { ProgressDialog(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAddressBookBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter = AddressBookFragmentPresenter(this, binding)
        viewModel = ViewModelProvider(requireActivity())[AddressBookViewModel::class.java].apply {
            addressBookLiveData.observe(viewLifecycleOwner) { presenter.bind(AddressBookFragmentModel(data = it)) }
            remoteEmptyLiveData.observe(viewLifecycleOwner) { presenter.bind(AddressBookFragmentModel(isRemoteEmpty = it)) }
            localEmptyLiveData.observe(viewLifecycleOwner) { presenter.bind(AddressBookFragmentModel(isLocalEmpty = it)) }
            onSearchStartLiveData.observe(viewLifecycleOwner) { presenter.bind(AddressBookFragmentModel(isSearchStart = it)) }
            showProgressLiveData.observe(viewLifecycleOwner) { if (it) progressDialog.show() else progressDialog.dismiss() }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAddressBook()
    }
}