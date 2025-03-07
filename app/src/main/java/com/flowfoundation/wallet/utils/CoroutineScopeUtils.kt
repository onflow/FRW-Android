package com.flowfoundation.wallet.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*

fun ioScope(unit: suspend () -> Unit) = CoroutineScope(Dispatchers.IO).launch { execute(unit) }

fun uiScope(unit: suspend () -> Unit) = CoroutineScope(Dispatchers.Main).launch { execute(unit) }

fun cpuScope(unit: suspend () -> Unit) = CoroutineScope(Dispatchers.Default).launch { execute(unit) }

fun viewModelIOScope(viewModel: ViewModel, unit: suspend () -> Unit) = viewModel.viewModelScope.launch(Dispatchers.IO) { execute(unit) }

private suspend fun execute(unit: suspend () -> Unit) {
    try {
        unit.invoke()
    } catch (e: Throwable) {
        loge(e)
    }
}