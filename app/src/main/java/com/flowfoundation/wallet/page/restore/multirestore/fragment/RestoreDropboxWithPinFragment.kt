package com.flowfoundation.wallet.page.restore.multirestore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentRestoreDropboxWithPinBinding
import com.flowfoundation.wallet.page.restore.multirestore.presenter.RestoreDropboxWithPinPresenter
import com.flowfoundation.wallet.page.restore.multirestore.viewmodel.MultiRestoreViewModel


class RestoreDropboxWithPinFragment: Fragment() {

    private lateinit var binding: FragmentRestoreDropboxWithPinBinding
    private lateinit var withPinPresenter: RestoreDropboxWithPinPresenter
    private lateinit var restoreViewModel: MultiRestoreViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestoreDropboxWithPinBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        withPinPresenter = RestoreDropboxWithPinPresenter(this)
        restoreViewModel = ViewModelProvider(requireActivity())[MultiRestoreViewModel::class.java].apply {
            dropboxOptionChangeLiveData.observe(requireActivity()) {
                withPinPresenter.bind(it)
            }
            toDropbox()
        }
    }
}