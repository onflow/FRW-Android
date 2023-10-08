package io.outblock.lilico.page.staking.guide

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.outblock.lilico.R
import io.outblock.lilico.databinding.DialogStakingSetupBinding
import io.outblock.lilico.manager.flowjvm.*
import io.outblock.lilico.manager.staking.StakingManager
import io.outblock.lilico.page.staking.providers.StakingProviderActivity
import io.outblock.lilico.utils.*

class StakingSetupDialog : BottomSheetDialogFragment() {

    private lateinit var binding: DialogStakingSetupBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogStakingSetupBinding.inflate(inflater)
        return binding.rootView
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding) {
            sendButton.setOnProcessing {
                setup()
            }
            closeButton.setOnClickListener {
                dismissAllowingStateLoss()
            }
        }
    }

    private fun setup() {
        ioScope {
            if (StakingManager.setup()) {
                StakingProviderActivity.launch(requireActivity())
            } else {
                toast(msg = getString(R.string.setup_fail))
                dismiss()
            }
        }
    }

    companion object {
        fun show(activity: FragmentActivity) {
            StakingSetupDialog().show(activity.supportFragmentManager, "")
        }
    }
}