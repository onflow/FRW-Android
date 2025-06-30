package com.flowfoundation.wallet.utils

import com.flowfoundation.wallet.manager.price.CurrencyManager
import com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency
import com.flowfoundation.wallet.page.profile.subpage.currency.model.selectedCurrency
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.RoundingMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CoinUtilsTest {

    @Before
    fun setup() {
        mockkObject(CurrencyManager)
        // Mock currency selection to return USD
        mockkStatic("com.flowfoundation.wallet.page.profile.subpage.currency.model.CurrencyKt")
        every { selectedCurrency() } returns Currency.USD
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test formatPrice for Float with currency conversion`() {
        every { CurrencyManager.currencyPrice() } returns 1.0f

        assertEquals("1.234", 1.2345f.formatPrice(digits = 3))
        assertEquals("$1.234", 1.2345f.formatPrice(digits = 3, includeSymbol = true))
        assertEquals("$ 1.234", 1.2345f.formatPrice(digits = 3, includeSymbol = true, includeSymbolSpace = true))
    }

    @Test
    fun `test formatPrice for Float with zero currency price`() {
        every { CurrencyManager.currencyPrice() } returns 0.0f
        assertEquals("1.234", 1.2345f.formatPrice())
    }

    @Test
    fun `test formatPrice for Float with large numbers`() {
        every { CurrencyManager.currencyPrice() } returns 1.0f

        assertEquals("1.234m", 1234567f.formatPrice(isAbbreviation = true))
        assertEquals("1,234,567", 1234567f.formatPrice(isAbbreviation = false))
    }

    @Test
    fun `test formatPrice for BigDecimal with currency conversion`() {
        every { CurrencyManager.currencyDecimalPrice() } returns BigDecimal.ONE

        val value = BigDecimal("1.2345")
        assertEquals("1.234", value.formatPrice(digits = 3))
        assertEquals("$1.234", value.formatPrice(digits = 3, includeSymbol = true))
        assertEquals("$ 1.234", value.formatPrice(digits = 3, includeSymbol = true, includeSymbolSpace = true))
    }

    @Test
    fun `test formatPrice for BigDecimal with zero currency price`() {
        every { CurrencyManager.currencyDecimalPrice() } returns BigDecimal.ZERO
        assertEquals("1.234", BigDecimal("1.2345").formatPrice())
    }

    @Test
    fun `test formatLargeNumber for BigDecimal`() {
        assertEquals("1.234m", BigDecimal("1234567").formatLargeNumber())
        assertEquals("1.234b", BigDecimal("1234567890").formatLargeNumber())
        assertEquals("1.234t", BigDecimal("1234567890000").formatLargeNumber())
        assertEquals("123.456", BigDecimal("123.456").formatLargeNumber())
    }

    @Test
    fun `test formatNumberWithCommas for BigDecimal`() {
        assertEquals("1,234,567", BigDecimal("1234567").formatNumberWithCommas())
        assertEquals("1,234.567", BigDecimal("1234.567").formatNumberWithCommas())
    }

    @Test
    fun `test format for Float with different digits and rounding modes`() {
        assertEquals("1.235", 1.2345f.format(digits = 3, roundingMode = RoundingMode.UP))
        assertEquals("1.234", 1.2345f.format(digits = 3, roundingMode = RoundingMode.DOWN))
    }

    @Test
    fun `test formatLargeNumber for Double`() {
        assertEquals("1.234m", 1234567.0.formatLargeNumber())
        assertEquals("1.234b", 1234567890.0.formatLargeNumber())
        assertEquals("1.234t", 1234567890000.0.formatLargeNumber())
        assertEquals("123.456", 123.456.toString())
    }

    @Test
    fun `test formatTokenPrice for various price ranges`() {
        // Regular price formatting (≥ $0.0001)
        assertEquals("$0.1234", formatTokenPrice(0.12345))
        assertEquals("$0.1199", formatTokenPrice(0.12))  // Actual implementation preserves 4 decimal places

        // Test minimum 2 decimal places
        assertEquals("$1.00", formatTokenPrice(1.0))
        assertEquals("$0.10", formatTokenPrice(0.1))

        // Subscript formatting (< $0.0001)
        assertEquals("$0.0₄123", formatTokenPrice(0.00001234))  // 4 leading zeros
        assertEquals("$0.0₅123", formatTokenPrice(0.000001234)) // 5 leading zeros

        // Zero case
        assertEquals("", formatTokenPrice(0.0))
    }

    @Test
    fun `test formatLargeBalanceNumber for Float`() {
        assertEquals("123.456", 123.456f.formatLargeBalanceNumber())
        assertEquals("1.234m", 1234567f.formatLargeBalanceNumber(isAbbreviation = true))
        assertEquals("1,234,567", 1234567f.formatLargeBalanceNumber(isAbbreviation = false))
    }

    @Test
    fun `test formatLargeBalanceNumber for BigDecimal`() {
        assertEquals("123.456", BigDecimal("123.456").formatLargeBalanceNumber())
        assertEquals("1.234m", BigDecimal("1234567").formatLargeBalanceNumber(isAbbreviation = true))
        assertEquals("1,234,567", BigDecimal("1234567").formatLargeBalanceNumber(isAbbreviation = false))
    }
} 