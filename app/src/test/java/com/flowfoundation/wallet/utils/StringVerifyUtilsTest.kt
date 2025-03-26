package com.flowfoundation.wallet.utils

import android.content.Context
import android.content.res.Resources
import com.flowfoundation.wallet.R
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StringVerifyUtilsTest {

    private val USERNAME_TOO_SHORT = "Username is too short"
    private val USERNAME_TOO_LONG = "Username is too long"
    private val USERNAME_FORMAT_WRONG = "Username format is wrong"

    // Create mocks with specific behavior
    private val mockResources = mock<Resources> {
        on { getString(R.string.username_too_short) } doReturn USERNAME_TOO_SHORT
        on { getString(R.string.username_too_long) } doReturn USERNAME_TOO_LONG
        on { getString(R.string.username_format_wrong) } doReturn USERNAME_FORMAT_WRONG
    }

    private val mockContext = mock<Context> {
        on { resources } doReturn mockResources
        on { getString(R.string.username_too_short) } doReturn USERNAME_TOO_SHORT
        on { getString(R.string.username_too_long) } doReturn USERNAME_TOO_LONG
        on { getString(R.string.username_format_wrong) } doReturn USERNAME_FORMAT_WRONG
    }

    @Before
    fun setup() {
        // Initialize with mock context
        Env.init(mockContext)
    }

    @Test
    fun `test verifyPassword with valid passwords`() {
        assertThat(verifyPassword("password123")).isTrue()
        assertThat(verifyPassword("longpassword12345")).isTrue()
        assertThat(verifyPassword("!@#$%^&*()")).isTrue()
    }

    @Test
    fun `test verifyPassword with invalid passwords`() {
        assertThat(verifyPassword("short")).isFalse()
        assertThat(verifyPassword("1234567")).isFalse()
        assertThat(verifyPassword("")).isFalse()
    }

    @Test
    fun `test usernameVerify with valid usernames`() {
        assertThat(usernameVerify("user123")).isNull()
        assertThat(usernameVerify("abc123")).isNull()
        assertThat(usernameVerify("User123")).isNull()
    }

    @Test
    fun `test usernameVerify with too short usernames`() {
        val result = usernameVerify("ab")
        assertThat(result).isEqualTo(USERNAME_TOO_SHORT)
    }

    @Test
    fun `test usernameVerify with too long usernames`() {
        val result = usernameVerify("thisusernameiswaytoolong")
        assertThat(result).isEqualTo(USERNAME_TOO_LONG)
    }

    @Test
    fun `test usernameVerify with invalid characters`() {
        val result = usernameVerify("user@123")
        assertThat(result).isEqualTo(USERNAME_FORMAT_WRONG)
    }

    @Test
    fun `test usernameVerify with edge cases`() {
        // Test minimum length (3 characters)
        assertThat(usernameVerify("abc")).isNull()
        
        // Test maximum length (15 characters)
        assertThat(usernameVerify("123456789012345")).isNull()
        
        // Test with spaces
        val resultWithSpaces = usernameVerify("user name")
        assertThat(resultWithSpaces).isEqualTo(USERNAME_FORMAT_WRONG)
        
        // Test with special characters
        val resultWithSpecialChars = usernameVerify("user-name")
        assertThat(resultWithSpecialChars).isEqualTo(USERNAME_FORMAT_WRONG)
    }
} 