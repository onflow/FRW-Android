package com.flowfoundation.wallet.page.walletrestore.fragments.driveusername

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.base.recyclerview.BaseAdapter
import com.flowfoundation.wallet.base.recyclerview.BaseViewHolder
import com.flowfoundation.wallet.base.recyclerview.getItemView
import com.flowfoundation.wallet.databinding.FragmentWalletRestoreDriveUsernameBinding
import com.flowfoundation.wallet.manager.drive.DriveItem
import com.flowfoundation.wallet.page.walletrestore.WALLET_RESTORE_STEP_DRIVE_PASSWORD
import com.flowfoundation.wallet.page.walletrestore.WalletRestoreViewModel
import com.flowfoundation.wallet.utils.extensions.dp2px
import com.flowfoundation.wallet.utils.findActivity
import com.flowfoundation.wallet.widgets.itemdecoration.ColorDividerItemDecoration

class WalletRestoreDriveUsernameFragment : Fragment() {

    private lateinit var binding: FragmentWalletRestoreDriveUsernameBinding

    private val data by lazy { arguments?.getParcelableArrayList<DriveItem>(EXTRA_DATA)!! }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentWalletRestoreDriveUsernameBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding.recyclerView) {
            adapter = UsernameAdapter().apply { setNewDiffData(data) }
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            addItemDecoration(ColorDividerItemDecoration(Color.TRANSPARENT, 12.dp2px().toInt(), LinearLayout.VERTICAL))
        }
    }

    companion object {
        private const val EXTRA_DATA = "EXTRA_DATA"

        fun instance(argument: ArrayList<DriveItem>?): WalletRestoreDriveUsernameFragment {
            return WalletRestoreDriveUsernameFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(EXTRA_DATA, argument)
                }
            }
        }
    }

}

private class UsernameAdapter : BaseAdapter<DriveItem>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return UsernameItemPresenter(parent.getItemView(R.layout.item_wallet_restore_username))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        (holder as UsernameItemPresenter).bind(item)
    }
}

private class UsernameItemPresenter(
    private val view: View,
) : BaseViewHolder(view), BasePresenter<DriveItem> {
    private val pageViewModel by lazy { ViewModelProvider(findActivity(view) as FragmentActivity)[WalletRestoreViewModel::class.java] }
    override fun bind(model: DriveItem) {
        view.findViewById<TextView>(R.id.username).text = model.username
        view.setOnClickListener { pageViewModel.changeStep(WALLET_RESTORE_STEP_DRIVE_PASSWORD, model) }
    }
}