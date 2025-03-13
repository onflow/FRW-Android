package com.flowfoundation.wallet.utils

import android.content.ClipboardManager
import android.content.Context
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.assertj.core.api.Assertions.assertThat

@RunWith(RobolectricTestRunner::class)
class ClipboardUtilsTest {

    private lateinit var clipboardManager: ClipboardManager

    @Before
    fun setup() {
        clipboardManager = RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    @Test
    fun `test copying normal text`() {
        val text = "Hello, World!"
        textToClipboard(text)
        
        val clipData = clipboardManager.primaryClip
        assertThat(clipData).isNotNull
        assertThat(clipData?.itemCount).isEqualTo(1)
        assertThat(clipData?.getItemAt(0)?.text.toString()).isEqualTo(text)
    }

    @Test
    fun `test copying empty text`() {
        val text = ""
        textToClipboard(text)
        
        val clipData = clipboardManager.primaryClip
        assertThat(clipData).isNotNull
        assertThat(clipData?.itemCount).isEqualTo(1)
        assertThat(clipData?.getItemAt(0)?.text.toString()).isEqualTo(text)
    }

    @Test
    fun `test copying special characters`() {
        val text = "!@#$%^&*()_+{}|:\"<>?~`-=[]\\;',./"
        textToClipboard(text)
        
        val clipData = clipboardManager.primaryClip
        assertThat(clipData).isNotNull
        assertThat(clipData?.itemCount).isEqualTo(1)
        assertThat(clipData?.getItemAt(0)?.text.toString()).isEqualTo(text)
    }

    @Test
    fun `test copying unicode characters`() {
        val text = "Hello 世界 👋 🌍"
        textToClipboard(text)
        
        val clipData = clipboardManager.primaryClip
        assertThat(clipData).isNotNull
        assertThat(clipData?.itemCount).isEqualTo(1)
        assertThat(clipData?.getItemAt(0)?.text.toString()).isEqualTo(text)
    }

    @Test
    fun `test copying long text`() {
        val text = "a".repeat(10000)
        textToClipboard(text)
        
        val clipData = clipboardManager.primaryClip
        assertThat(clipData).isNotNull
        assertThat(clipData?.itemCount).isEqualTo(1)
        assertThat(clipData?.getItemAt(0)?.text.toString()).isEqualTo(text)
    }

    @Test
    fun `test copying multiple times`() {
        val texts = listOf("First text", "Second text", "Third text")
        
        texts.forEach { text ->
            textToClipboard(text)
            val clipData = clipboardManager.primaryClip
            assertThat(clipData).isNotNull
            assertThat(clipData?.itemCount).isEqualTo(1)
            assertThat(clipData?.getItemAt(0)?.text.toString()).isEqualTo(text)
        }
    }
} 