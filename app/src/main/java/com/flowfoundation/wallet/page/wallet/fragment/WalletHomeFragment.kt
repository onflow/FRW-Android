package com.flowfoundation.wallet.page.wallet.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentWalletHomeBinding
import com.flowfoundation.wallet.firebase.auth.isUserSignIn
import com.flowfoundation.wallet.page.main.MainActivityViewModel
import com.flowfoundation.wallet.page.wallet.WalletFragment
import com.flowfoundation.wallet.utils.isRegistered
import com.flowfoundation.wallet.utils.uiScope


class WalletHomeFragment : Fragment() {

    private lateinit var binding: FragmentWalletHomeBinding

    private val pageViewModel by lazy { ViewModelProvider(requireActivity())[MainActivityViewModel::class.java] }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWalletHomeBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        uiScope { bindFragment() }
        pageViewModel.walletRegisterSuccessLiveData.observe(viewLifecycleOwner) { uiScope { bindFragment() } }
    }

    private suspend fun bindFragment() {
        childFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, if (isRegistered() && isUserSignIn()) WalletFragment() else WalletUnregisteredFragment())
            .commitAllowingStateLoss()
    }
}