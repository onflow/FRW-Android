package com.flowfoundation.wallet.page.backup.multibackup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.FragmentBackupRecoveryPhraseBinding
import com.flowfoundation.wallet.page.backup.model.BackupType
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupCompletedItem
import com.flowfoundation.wallet.page.backup.multibackup.model.BackupRecoveryPhraseOption
import com.flowfoundation.wallet.page.backup.multibackup.presenter.BackupRecoveryPhrasePresenter
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.BackupRecoveryPhraseViewModel
import com.flowfoundation.wallet.page.backup.multibackup.viewmodel.MultiBackupViewModel
import com.flowfoundation.wallet.page.walletcreate.fragments.mnemonic.MnemonicAdapter
import com.flowfoundation.wallet.utils.extensions.gone
import com.flowfoundation.wallet.utils.extensions.res2String
import com.flowfoundation.wallet.utils.extensions.visible
import com.flowfoundation.wallet.widgets.itemdecoration.GridSpaceItemDecoration
import com.instabug.library.Instabug


class BackupRecoveryPhraseFragment : Fragment() {

    private lateinit var binding: FragmentBackupRecoveryPhraseBinding
    private lateinit var viewModel: BackupRecoveryPhraseViewModel
    private lateinit var presenter: BackupRecoveryPhrasePresenter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBackupRecoveryPhraseBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter = BackupRecoveryPhrasePresenter(this)
        viewModel = ViewModelProvider(this)[BackupRecoveryPhraseViewModel::class.java].apply {
            optionChangeLiveData.observe(viewLifecycleOwner) {
                presenter.bind(it)
            }
            changeOption(BackupRecoveryPhraseOption.BACKUP_WARING)
        }
    }

}