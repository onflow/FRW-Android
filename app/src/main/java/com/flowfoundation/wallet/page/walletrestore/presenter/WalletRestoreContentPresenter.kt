package com.flowfoundation.wallet.page.walletrestore.presenter

import androidx.transition.Fade
import androidx.transition.Transition
import com.google.android.material.transition.MaterialSharedAxis
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.base.presenter.BasePresenter
import com.flowfoundation.wallet.manager.drive.DriveItem
import com.flowfoundation.wallet.page.restore.multirestore.model.RestoreErrorOption
import com.flowfoundation.wallet.page.walletcreate.getRootIdByStep
import com.flowfoundation.wallet.page.walletrestore.*
import com.flowfoundation.wallet.page.walletrestore.fragments.drivepwd.WalletRestoreDrivePasswordFragment
import com.flowfoundation.wallet.page.walletrestore.fragments.driveusername.WalletRestoreDriveUsernameFragment
import com.flowfoundation.wallet.page.walletrestore.fragments.error.WalletRestoreErrorFragment
import com.flowfoundation.wallet.page.walletrestore.fragments.guide.WalletRestoreGuideFragment
import com.flowfoundation.wallet.page.walletrestore.fragments.mnemonic.WalletRestoreMnemonicFragment
import com.flowfoundation.wallet.page.walletrestore.model.WalletRestoreContentModel


class WalletRestoreContentPresenter(
    private val activity: WalletRestoreActivity,
) : BasePresenter<WalletRestoreContentModel> {

    private var currentStep = -1

    override fun bind(model: WalletRestoreContentModel) {
        model.changeStep?.let { onStepChane(it.first, it.second) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun onStepChane(step: Int, arguments: Any?) {
        val transition = createTransition(currentStep, step)
        val fragment = when (step) {
            WALLET_RESTORE_STEP_GUIDE -> WalletRestoreGuideFragment()
            WALLET_RESTORE_STEP_DRIVE_USERNAME -> WalletRestoreDriveUsernameFragment.instance(arguments as? ArrayList<DriveItem>)
            WALLET_RESTORE_STEP_DRIVE_PASSWORD -> {
                val data = if (arguments is DriveItem) arguments else (arguments as? List<DriveItem>)?.firstOrNull()
                WalletRestoreDrivePasswordFragment.instance(data)
            }
            WALLET_RESTORE_STEP_MNEMONIC -> WalletRestoreMnemonicFragment()
            WALLET_RESTORE_ERROR -> WalletRestoreErrorFragment(RestoreErrorOption.RESTORE_FAILED)
            WALLET_RESTORE_BACKUP_NOT_FOUND-> WalletRestoreErrorFragment(RestoreErrorOption.BACKUP_NOT_FOUND)
            else -> WalletRestoreGuideFragment()
        }
        fragment.enterTransition = transition
        activity.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).commit()
        currentStep = step
    }

    private fun createTransition(currentStep: Int, newStep: Int): Transition {
        if (currentStep < 0) {
            return Fade().apply { duration = 50 }
        }
        val transition = MaterialSharedAxis(MaterialSharedAxis.X, currentStep < newStep)

        transition.addTarget(getRootIdByStep(currentStep))
        transition.addTarget(getRootIdByStep(newStep))
        return transition
    }
}