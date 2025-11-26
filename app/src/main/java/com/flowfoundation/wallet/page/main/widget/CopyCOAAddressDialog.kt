package com.flowfoundation.wallet.page.main.widget

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.utils.textToClipboard
import com.flowfoundation.wallet.utils.toast
import com.flowfoundation.wallet.widgets.SendButton

class CopyCOAAddressDialog(
    private val context: Context,
    private val address: String
) {
    private val TAG = CopyCOAAddressDialog::class.java.simpleName

    fun show() {
        var dialog: Dialog? = null

        val alertDialogBuilder = AlertDialog.Builder(context, R.style.Theme_AlertDialogTheme)

        // Create a FrameLayout to hold our Compose content
        val frameLayout = FrameLayout(context)
        val composeView = ComposeView(context).apply {
            setContent {
                CopyCOAAddressContent(
                    address = address,
                    onConfirmCopy = {
                        logd(TAG, "Copying COA address to clipboard: $address")
                        textToClipboard(address)
                        toast(msgRes = R.string.copied_to_clipboard)
                        dialog?.dismiss()
                    }
                )
            }
        }

        frameLayout.addView(composeView)

        alertDialogBuilder.setView(frameLayout)
        dialog = alertDialogBuilder.create()
        dialog.show()
    }
}

@Composable
private fun CopyCOAAddressContent(
    address: String,
    onConfirmCopy: () -> Unit
) {
    Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(colorResource(R.color.background))
          .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.evm_on_flow_address),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.text_1)
        )

        ConstraintLayout(
            modifier = Modifier
              .background(
                color = colorResource(R.color.evm),
                shape = RoundedCornerShape(16.dp)
              )
        ) {
            val (evmLabel, flowLabel) = createRefs()

            Text(
                text = stringResource(R.string.label_evm),
                color = colorResource(id = R.color.white),
                fontSize = 8.sp,
                modifier = Modifier.constrainAs(evmLabel) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start, margin = 4.dp)
                    end.linkTo(flowLabel.start)
                }
            )

            Box(
                modifier = Modifier
                  .constrainAs(flowLabel) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(evmLabel.end, margin = 4.dp)
                    end.linkTo(parent.end)
                  }
                  .background(
                    color = colorResource(R.color.evm_on_flow_end_color),
                    shape = RoundedCornerShape(16.dp)
                  )
                  .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = stringResource(R.string.label_flow),
                    color = colorResource(id = R.color.black),
                    fontSize = 8.sp
                )
            }
        }

        Text(
            text = stringResource(R.string.confirm_copy_address_tips),
            fontSize = 14.sp,
            color = colorResource(R.color.text_2),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = colorResource(R.color.bg_card),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            Text(
                text = address,
                fontSize = 14.sp,
                color = colorResource(R.color.text_1),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SendButtonWrapper(
            onProcessing = onConfirmCopy
        )
    }
}

@SuppressLint("UseCompatLoadingForDrawables")
@Composable
private fun SendButtonWrapper(
    onProcessing: () -> Unit
) {
    // We need to use AndroidView to wrap the SendButton widget
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            SendButton(context).apply {
                // Set default text
                updateDefaultText(R.string.confirm_to_copy_address)

                // Set card properties
                setCardBackgroundColor(context.getColor(R.color.button_color))
                radius = context.resources.getDimension(R.dimen.send_button_radius)
                cardElevation = context.resources.getDimension(R.dimen.send_button_elevation)

                // Set ripple color
                val rippleColor = context.getColor(R.color.primary10)
                foreground = context.getDrawable(android.R.drawable.list_selector_background)?.apply {
                    setTint(rippleColor)
                }

                setOnProcessing { onProcessing() }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(top = 10.dp)
    )
}
