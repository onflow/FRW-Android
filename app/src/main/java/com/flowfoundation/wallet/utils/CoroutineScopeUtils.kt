package com.flowfoundation.wallet.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.*

fun ioScope(unit: suspend () -> Unit) = CoroutineScope(Dispatchers.IO).launch { execute(unit) }

fun uiScope(unit: suspend () -> Unit) = CoroutineScope(Dispatchers.Main).launch { execute(unit) }

fun cpuScope(unit: suspend () -> Unit) = CoroutineScope(Dispatchers.Default).launch { execute(unit) }

fun viewModelIOScope(viewModel: ViewModel, unit: suspend () -> Unit) = viewModel.viewModelScope.launch(Dispatchers.IO) { execute(unit) }

/**
 * FIXED: Background-safe IO scope that checks app lifecycle before executing
 * This prevents ANR issues when expensive operations are triggered while app is in background
 */
fun backgroundSafeIoScope(unit: suspend () -> Unit) = CoroutineScope(Dispatchers.IO).launch { 
    // Check if app is in foreground before executing potentially expensive operations
    if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
        execute(unit)
    } else {
        logd("CoroutineScopeUtils", "Skipping background operation - app not in foreground")
    }
}

private suspend fun execute(unit: suspend () -> Unit) {
    try {
        unit.invoke()
    } catch (e: Throwable) {
        loge(e)
    }
}