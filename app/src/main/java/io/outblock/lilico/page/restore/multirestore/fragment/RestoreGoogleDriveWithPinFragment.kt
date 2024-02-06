package io.outblock.lilico.page.restore.multirestore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import io.outblock.lilico.databinding.FragmentRestoreGoogleDriveWithPinBinding
import io.outblock.lilico.page.restore.multirestore.model.RestoreGoogleDriveOption
import io.outblock.lilico.page.restore.multirestore.presenter.RestoreGoogleDriveWithPinPresenter
import io.outblock.lilico.page.restore.multirestore.viewmodel.RestoreGoogleDriveWithPinViewModel


class RestoreGoogleDriveWithPinFragment: Fragment() {

    private lateinit var binding: FragmentRestoreGoogleDriveWithPinBinding
    private lateinit var withPinPresenter: RestoreGoogleDriveWithPinPresenter
    private lateinit var withPinViewModel: RestoreGoogleDriveWithPinViewModel

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
        withPinViewModel = ViewModelProvider(this)[RestoreGoogleDriveWithPinViewModel::class.java].apply {
            optionChangeLiveData.observe(viewLifecycleOwner) {
                withPinPresenter.bind(it)
            }
            changeOption(RestoreGoogleDriveOption.RESTORE_PIN)
        }
    }
}