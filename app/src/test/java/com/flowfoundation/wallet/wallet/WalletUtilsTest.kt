package com.flowfoundation.wallet.wallet

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class WalletUtilsTest {

    @Test
    fun `test toAddress with various prefixes`() {
        // Test cases for different address formats
        assertEquals("0x123", "0x123".toAddress())
        assertEquals("0x123", "0x0x123".toAddress())
        assertEquals("0x123", "Fx123".toAddress())
    }

    @Test
    fun `test removeAddressPrefix with various formats`() {
        // Test cases for different address formats
        assertEquals("123", "0x123".removeAddressPrefix())
        assertEquals("123", "0x0x123".removeAddressPrefix())
        assertEquals("123", "Fx123".removeAddressPrefix())
    }
}