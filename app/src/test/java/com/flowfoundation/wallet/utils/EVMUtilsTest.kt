package com.flowfoundation.wallet.utils

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.assertj.core.api.Assertions.assertThat

@RunWith(RobolectricTestRunner::class)
class EVMUtilsTest {

    @Test
    fun `test shortenEVMString with valid EVM address`() {
        val address = "0x742d35Cc6634C0532925a3b844Bc454e4438f44e"
        val shortened = shortenEVMString(address)
        assertThat(shortened).isEqualTo("0x742d35C...438f44e")
    }

    @Test
    fun `test shortenEVMString with null input`() {
        val shortened = shortenEVMString(null)
        assertThat(shortened).isEmpty()
    }

    @Test
    fun `test shortenEVMString with 0x input`() {
        val shortened = shortenEVMString("0x")
        assertThat(shortened).isEmpty()
    }

    @Test
    fun `test shortenEVMString with short input`() {
        val address = "0x1234567890"
        val shortened = shortenEVMString(address)
        assertThat(shortened).isEqualTo(address)
    }

    @Test
    fun `test shortenEVMString with non-EVM address`() {
        val input = "not-an-evm-address"
        val shortened = shortenEVMString(input)
        assertThat(shortened).isEqualTo(input)
    }

    @Test
    fun `test shortenEVMString with lowercase address`() {
        val address = "0x742d35cc6634c0532925a3b844bc454e4438f44e"
        val shortened = shortenEVMString(address)
        assertThat(shortened).isEqualTo("0x742d35c...438f44e")
    }

    @Test
    fun `test shortenEVMString with mixed case address`() {
        val address = "0x742D35cC6634C0532925a3b844Bc454e4438F44e"
        val shortened = shortenEVMString(address)
        assertThat(shortened).isEqualTo("0x742D35c...438F44e")
    }

    @Test
    fun `test shortenEVMString preserves case in output`() {
        val addresses = listOf(
            "0x742d35cc6634c0532925a3b844bc454e4438f44e",
            "0x742D35CC6634C0532925A3B844BC454E4438F44E",
            "0x742d35Cc6634c0532925a3b844Bc454e4438f44E"
        )

        addresses.forEach { address ->
            val shortened = shortenEVMString(address)
            val prefix = address.substring(0, 9)
            val suffix = address.substring(address.length - 7)
            assertThat(shortened).isEqualTo("$prefix...$suffix")
        }
    }

    @Test
    fun `test shortenEVMString with invalid hex characters`() {
        val address = "0xGHIJKLMNOPQRSTUVWXYZ1234567890abcdef42"
        val shortened = shortenEVMString(address)
        assertThat(shortened).isEqualTo(address)
    }

    @Test
    fun `test shortenEVMString with address missing 0x prefix`() {
        val address = "742d35cc6634c0532925a3b844bc454e4438f44e"
        val shortened = shortenEVMString(address)
        assertThat(shortened).isEqualTo(address)
    }
}
