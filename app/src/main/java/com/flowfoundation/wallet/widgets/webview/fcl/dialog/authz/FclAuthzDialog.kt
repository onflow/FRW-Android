package com.flowfoundation.wallet.widgets.webview.fcl.dialog.authz

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.flowfoundation.wallet.page.dialog.linkaccount.LINK_ACCOUNT_TAG
import com.flowfoundation.wallet.widgets.webview.fcl.model.FclDialogModel

class FclAuthzDialog : BottomSheetDialogFragment() {

    private val data by lazy { arguments?.getParcelable<FclDialogModel>(EXTRA_DATA) }

    private lateinit var contentView: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        instance = this
        contentView = if (isLinkAccount()) FclAuthzLinkAccountView(requireContext()) else FclAuthzView(requireContext())
        return contentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (data == null) {
            dismiss()
            return
        }

        if (contentView is FclAuthzView) {
            (contentView as FclAuthzView).setup(data!!) { approveCallback?.invoke(it) }
        } else if (contentView is FclAuthzLinkAccountView) {
            (contentView as FclAuthzLinkAccountView).setup(data!!) { approveCallback?.invoke(it) }
        }
    }

    fun isLinkAccount() = data?.cadence?.trim().orEmpty().startsWith(LINK_ACCOUNT_TAG)

    override fun onCancel(dialog: DialogInterface) {
        approveCallback?.invoke(false)
    }

    override fun onDestroy() {
        instance = null
        approveCallback = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_DATA = "data"

        private var approveCallback: ((isApprove: Boolean) -> Unit)? = null

        @SuppressLint("StaticFieldLeak")
        private var instance: FclAuthzDialog? = null

        fun observe(callback: (isApprove: Boolean) -> Unit) {
            this.approveCallback = callback
        }

        fun isShowing() = (instance?.dialog?.isShowing ?: false) && !(instance?.isRemoving ?: true)

        fun show(
            fragmentManager: FragmentManager,
            data: FclDialogModel,
        ) {
            if (instance != null) {
                return
            }
            FclAuthzDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(EXTRA_DATA, data)
                }
            }.show(fragmentManager, "")
        }

        fun dismiss(forceDismiss: Boolean = false) {
            val dialog = instance ?: return
            // link account close by he self
            if (!dialog.isLinkAccount() || forceDismiss) {
                dialog.dismiss()
            }
        }
    }
}