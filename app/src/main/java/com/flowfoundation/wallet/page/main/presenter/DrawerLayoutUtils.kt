package com.flowfoundation.wallet.page.main.presenter

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.flowfoundation.wallet.page.main.MainActivityViewModel

fun openDrawerLayout(context: Context) {
    val activity = context as? FragmentActivity ?: return
    val viewModel = ViewModelProvider(activity)[MainActivityViewModel::class.java]
    viewModel.openDrawerLayout()
}
