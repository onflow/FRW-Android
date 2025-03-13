package com.flowfoundation.wallet.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class ByteUtilsTest {

    @Before
    fun setup() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `test toHumanReadableSIPrefixes with bytes`() {
        assertThat(toHumanReadableSIPrefixes(0)).isEqualTo("0 Bytes")
        assertThat(toHumanReadableSIPrefixes(1)).isEqualTo("1 Bytes")
        assertThat(toHumanReadableSIPrefixes(900)).isEqualTo("900 Bytes")
    }

    @Test
    fun `test toHumanReadableSIPrefixes with kilobytes`() {
        assertThat(toHumanReadableSIPrefixes(1000)).isEqualTo("1 KB")
        assertThat(toHumanReadableSIPrefixes(1500)).isEqualTo("1.5 KB")
        assertThat(toHumanReadableSIPrefixes(999500)).isEqualTo("999.5 KB")
    }

    @Test
    fun `test toHumanReadableSIPrefixes with megabytes`() {
        assertThat(toHumanReadableSIPrefixes(1000000)).isEqualTo("1 MB")
        assertThat(toHumanReadableSIPrefixes(1500000)).isEqualTo("1.5 MB")
        assertThat(toHumanReadableSIPrefixes(999500000)).isEqualTo("999.5 MB")
    }

    @Test
    fun `test toHumanReadableSIPrefixes with gigabytes`() {
        assertThat(toHumanReadableSIPrefixes(1000000000)).isEqualTo("1 GB")
        assertThat(toHumanReadableSIPrefixes(1500000000)).isEqualTo("1.5 GB")
        assertThat(toHumanReadableSIPrefixes(999500000000)).isEqualTo("999.5 GB")
    }

    @Test
    fun `test toHumanReadableSIPrefixes with terabytes`() {
        assertThat(toHumanReadableSIPrefixes(1000000000000)).isEqualTo("1 TB")
        assertThat(toHumanReadableSIPrefixes(1500000000000)).isEqualTo("1.5 TB")
        assertThat(toHumanReadableSIPrefixes(999500000000000)).isEqualTo("999.5 TB")
    }

    @Test
    fun `test toHumanReadableSIPrefixes with petabytes`() {
        assertThat(toHumanReadableSIPrefixes(1000000000000000)).isEqualTo("1 PB")
        assertThat(toHumanReadableSIPrefixes(1500000000000000)).isEqualTo("1.5 PB")
        assertThat(toHumanReadableSIPrefixes(999500000000000000)).isEqualTo("999.5 PB")
    }

    @Test
    fun `test toHumanReadableSIPrefixes with exabytes`() {
        assertThat(toHumanReadableSIPrefixes(1000000000000000000)).isEqualTo("1 EB")
        assertThat(toHumanReadableSIPrefixes(1500000000000000000)).isEqualTo("1.5 EB")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test toHumanReadableSIPrefixes with negative values`() {
        toHumanReadableSIPrefixes(-1)
    }
} 