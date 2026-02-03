package com.flowfoundation.wallet.page.restore.mnemonic.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.page.restore.mnemonic.RestoreMnemonicViewModel
import kotlin.text.split

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreMnemonicScreen(
    viewModel: RestoreMnemonicViewModel = viewModel(),
    onClose: () -> Unit,
    onRestoreSuccess: () -> Unit
) {
    val isRestoring by viewModel.isRestoring.collectAsState()
    val restoreSuccess by viewModel.restoreSuccess.collectAsState()
    var mnemonic by remember { mutableStateOf("") }
    var isValid by remember { mutableStateOf(false) }

    LaunchedEffect(restoreSuccess) {
        if (restoreSuccess) {
            onRestoreSuccess()
        }
    }

    LaunchedEffect(mnemonic) {
        isValid = viewModel.validateMnemonic(mnemonic.trim())
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Restore Seed Phrase",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorResource(id = R.color.text),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colorResource(id = R.color.icon)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorResource(id = R.color.background),
                    titleContentColor = colorResource(id = R.color.text),
                    actionIconContentColor = colorResource(id = R.color.icon)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(id = R.color.background))
                .padding(paddingValues)
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Please enter your 12 or 15-word recovery phrase to enable all features.",
                    fontSize = 14.sp,
                    color = colorResource(id = R.color.text_2), // Use text_2 for secondary text
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                OutlinedTextField(
                    value = mnemonic,
                    onValueChange = { mnemonic = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("Enter recovery phrase", color = colorResource(id = R.color.text_3)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colorResource(id = R.color.bg_2), // Use bg_2 for input background
                        unfocusedContainerColor = colorResource(id = R.color.bg_2),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = colorResource(id = R.color.colorSecondary),
                        focusedTextColor = colorResource(id = R.color.text),
                        unfocusedTextColor = colorResource(id = R.color.text)
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { viewModel.restoreMnemonic(mnemonic.split(" ").filter { it.isNotBlank() }.joinToString(" ") { it }) },
                    enabled = isValid && !isRestoring,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorResource(id = R.color.button),
                        contentColor = colorResource(id = R.color.button_text),
                        disabledContainerColor = colorResource(id = R.color.disable).copy(alpha = 0.5f),
                        disabledContentColor = colorResource(id = R.color.button_text).copy(alpha = 0.8f)
                    )
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Restore",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
