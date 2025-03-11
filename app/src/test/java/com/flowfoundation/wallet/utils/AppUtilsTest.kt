package com.flowfoundation.wallet.utils

import android.app.Activity
import android.content.res.Configuration
import com.flowfoundation.wallet.firebase.storage.firebaseImage
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppUtilsTest {

    private lateinit var activityController: ActivityController<Activity>
    private lateinit var activity: Activity

    @Before
    fun setup() {
        activityController = Robolectric.buildActivity(Activity::class.java)
        activity = activityController.get()
    }

    @After
    fun tearDown() {
        activityController.destroy()
        unmockkAll()
    }

    @Test
    fun `test isNightMode with night mode enabled`() {
        val configuration = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
        }
        activity.resources.configuration.uiMode = configuration.uiMode
        
        assertTrue(isNightMode(activity))
    }

    @Test
    fun `test isNightMode with night mode disabled`() {
        val configuration = Configuration().apply {
            uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
        }
        activity.resources.configuration.uiMode = configuration.uiMode
        
        assertFalse(isNightMode(activity))
    }

    @Test
    fun `test parseAvatarUrl with null input`() {
        val nullString: String? = null
        assertEquals("", nullString.parseAvatarUrl())
    }

    @Test
    fun `test parseAvatarUrl with empty input`() {
        assertEquals("", "".parseAvatarUrl())
    }

    @Test
    fun `test parseAvatarUrl with valid input`() {
        mockkStatic("com.flowfoundation.wallet.firebase.storage.FirebaseStorageKt")
        val testUrl = "test_image.jpg"
        every { any<String>().firebaseImage() } returns "https://firebase.storage.url/$testUrl"
        
        val result = testUrl.parseAvatarUrl()
        assertTrue(result.contains("https://firebase.storage.url/"))
    }
}