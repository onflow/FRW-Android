package com.flowfoundation.wallet.page.restore.keystore.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.databinding.FragmentPrivateKeyStoreUsernameBinding
import com.flowfoundation.wallet.page.restore.keystore.presenter.PrivateKeyStoreUsernamePresenter
import com.flowfoundation.wallet.page.walletcreate.fragments.username.WalletCreateUsernameModel
import com.flowfoundation.wallet.page.walletcreate.fragments.username.WalletCreateUsernameViewModel


class PrivateKeyStoreUsernameFragment: Fragment() {

    private lateinit var binding: FragmentPrivateKeyStoreUsernameBinding
    private lateinit var presenter: PrivateKeyStoreUsernamePresenter
    private lateinit var viewModel: WalletCreateUsernameViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPrivateKeyStoreUsernameBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter = PrivateKeyStoreUsernamePresenter(this, binding)
        viewModel = ViewModelProvider(this)[WalletCreateUsernameViewModel::class.java].apply {
            usernameStateLiveData.observe(viewLifecycleOwner) { presenter.bind(
                WalletCreateUsernameModel(state = it)
            ) }
        }
    }

    override fun onDestroyView() {
        presenter.unbind()
        super.onDestroyView()
    }
}