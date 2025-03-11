package com.flowfoundation.wallet.page.backup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.flowfoundation.wallet.databinding.FragmentRestorePinCodeBinding
import com.flowfoundation.wallet.page.backup.presenter.ViewPinCodePresenter


class ViewPinCodeFragment: Fragment() {
    private lateinit var binding: FragmentRestorePinCodeBinding
    private lateinit var presenter: ViewPinCodePresenter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestorePinCodeBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter = ViewPinCodePresenter(this, binding)
    }
}