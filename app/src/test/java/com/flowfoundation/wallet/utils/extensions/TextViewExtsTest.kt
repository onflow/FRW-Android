package com.flowfoundation.wallet.utils.extensions

import android.content.Context
import android.text.InputFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TextViewExtsTest {

    private lateinit var editText: EditText
    private lateinit var textView: TextView
    @Mock
    private lateinit var imm: InputMethodManager
    @Mock
    private lateinit var context: Context

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        val appContext = RuntimeEnvironment.getApplication()
        editText = EditText(appContext)
        textView = TextView(appContext)
        `when`(context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE)).thenReturn(imm)
    }

    @Test
    fun `test hideKeyboard`() {
        val editTextSpy = spy(editText)
        doReturn(context).`when`(editTextSpy).context

        editTextSpy.hideKeyboard()
        verify(imm).hideSoftInputFromWindow(editTextSpy.windowToken, 0)
    }

    @Test
    fun `test showKeyboard`() {
        val editTextSpy = spy(editText)
        doReturn(context).`when`(editTextSpy).context

        editTextSpy.showKeyboard()
        verify(imm).showSoftInput(editTextSpy, 0)
    }

    @Test
    fun `test setDecimalDigitsFilter`() {
        editText.setDecimalDigitsFilter(2)
        val filter = editText.filters.firstOrNull() as InputFilter

        // Test valid decimal numbers
        var dest = SpannableString("")
        assertEquals(null, filter.filter("1", 0, 1, dest, 0, 0))
        
        dest = SpannableString("1")
        assertEquals(null, filter.filter(".", 0, 1, dest, 1, 1))
        
        dest = SpannableString("1.")
        assertEquals(null, filter.filter("2", 0, 1, dest, 2, 2))
        
        // Test invalid input
        dest = SpannableString("")
        assertEquals("", filter.filter("a", 0, 1, dest, 0, 0))
        
        // Test decimal limit
        dest = SpannableString("1.23")
        assertEquals("", filter.filter("4", 0, 1, dest, 4, 4))
        
        // Test multiple decimal points
        dest = SpannableString("1.2")
        assertEquals("", filter.filter(".", 0, 1, dest, 3, 3))
    }

    @Test
    fun `test setSafeSpan`() {
        val text = "Hello World"
        val spannableString = SpannableString(text).setSafeSpan("World", ForegroundColorSpan(0xFFFF0000.toInt()))

        val spans = (spannableString as Spanned).getSpans(0, spannableString.length, ForegroundColorSpan::class.java)
        assertEquals(1, spans.size)
    }

    @Test
    fun `test setSpannableText`() {
        val text = "Hello World"
        textView.setSpannableText(text, "World", 0xFFFF0000.toInt())

        val textViewText = textView.text
        val spans = (textViewText as Spanned).getSpans(0, textViewText.length, ForegroundColorSpan::class.java)
        assertEquals(1, spans.size)
    }
}
