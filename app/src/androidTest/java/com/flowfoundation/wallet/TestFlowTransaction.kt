package com.flowfoundation.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class TestFlowTransaction {

    init {
        System.loadLibrary("TrustWalletCore")
    }
}