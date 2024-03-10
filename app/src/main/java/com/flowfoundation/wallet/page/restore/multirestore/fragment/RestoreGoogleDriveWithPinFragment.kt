package com.flowfoundation.wallet.page.restore.multirestore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentRestoreGoogleDriveWithPinBinding
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreGoogleDriveOption
import com.flowfoundation.wallet.page.restore.multirestore.presenter.RestoreGoogleDriveWithPinPresenter
import com.flowfoundation.wallet.page.restore.multirestore.viewmodel.MultiRestoreViewModel


class RestoreGoogleDriveWithPinFragment: Fragment() {

    private lateinit var binding: FragmentRestoreGoogleDriveWithPinBinding
    private lateinit var withPinPresenter: RestoreGoogleDriveWithPinPresenter
    private lateinit var restoreViewModel: MultiRestoreViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestoreGoogleDriveWithPinBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        withPinPresenter = RestoreGoogleDriveWithPinPresenter(this)
        restoreViewModel = ViewModelProvider(requireActivity())[MultiRestoreViewModel::class.java].apply {
            googleDriveOptionChangeLiveData.observe(requireActivity()) {
                withPinPresenter.bind(it)
            }
            changeGoogleDriveOption(RestoreGoogleDriveOption.RESTORE_GOOGLE_DRIVE)
        }
    }
}