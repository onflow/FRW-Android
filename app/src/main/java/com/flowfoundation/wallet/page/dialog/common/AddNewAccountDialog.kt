package com.flowfoundation.wallet.page.dialog.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import com.flowfoundation.wallet.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddNewAccountDialog : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AddNewAccountContent(
                    canAddCadence = canAddCadence,
                    canAddEOA = canAddEOA,
                    onCadenceClick = {
                        dismiss()
                        onCadenceClick?.invoke()
                    },
                    onEOAClick = {
                        dismiss()
                        onEOAClick?.invoke()
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onCadenceClick = null
        onEOAClick = null
    }

    companion object {
        private var onCadenceClick: (() -> Unit)? = null
        private var onEOAClick: (() -> Unit)? = null
        private var canAddCadence: Boolean = true
        private var canAddEOA: Boolean = true

        fun show(
            fragmentManager: FragmentManager,
            canAddCadence: Boolean = true,
            canAddEOA: Boolean = true,
            onCadence: () -> Unit,
            onEOA: () -> Unit
        ) {
            this.onCadenceClick = onCadence
            this.onEOAClick = onEOA
            this.canAddCadence = canAddCadence
            this.canAddEOA = canAddEOA
            AddNewAccountDialog().showNow(fragmentManager, "AddNewAccountDialog")
        }
    }
}

@Composable
private fun AddNewAccountContent(
    canAddCadence: Boolean,
    canAddEOA: Boolean,
    onCadenceClick: () -> Unit,
    onEOAClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colorResource(id = R.color.deep_bg),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = colorResource(id = R.color.bg_card),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .padding(horizontal = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (canAddCadence) 1f else 0.4f)
                    .clickable(enabled = canAddCadence) { onCadenceClick() }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add_cadence_account),
                    contentDescription = null,
                    tint = colorResource(id = R.color.text_1),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.create_new_cadence_account),
                    color = colorResource(id = R.color.text_1),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(
                color = colorResource(id = R.color.border_line_stroke),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (canAddEOA) 1f else 0.4f)
                    .clickable(enabled = canAddEOA) { onEOAClick() }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add_eoa_account),
                    contentDescription = null,
                    tint = colorResource(id = R.color.text_1),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.create_new_eoa_account),
                    color = colorResource(id = R.color.text_1),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
