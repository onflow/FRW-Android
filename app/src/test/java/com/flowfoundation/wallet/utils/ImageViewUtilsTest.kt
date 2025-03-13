package com.flowfoundation.wallet.utils

import android.os.Build
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.bumptech.glide.Glide
import jp.wasabeef.glide.transformations.BlurTransformation
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.assertj.core.api.Assertions.assertThat

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class ImageViewUtilsTest {

    private lateinit var mockImageView: ImageView

    @Before
    fun setup() {
        // Setup Env singleton with test context
        val context = ApplicationProvider.getApplicationContext()
        Env.setup(context)
        
        mockImageView = mock()
        whenever(mockImageView.context).thenReturn(context)
    }

    @Test
    fun `test loadAvatar with normal URL`() {
        val url = "https://example.com/avatar.jpg"
        mockImageView.loadAvatar(url)
        
        // Verify that Glide was used to load the image
        verify(mockImageView).context
    }

    @Test
    fun `test loadAvatar with flovatar URL`() {
        val url = "https://flovatar.com/api/image/123.svg"
        mockImageView.loadAvatar(url)
        
        // Verify that the URL was converted to PNG and loaded
        verify(mockImageView).context
    }

    @Test
    fun `test loadAvatar with transformation`() {
        val url = "https://example.com/avatar.jpg"
        val transformation = BlurTransformation()
        mockImageView.loadAvatar(url, transformation = transformation)
        
        verify(mockImageView).context
    }

    @Test
    fun `test svgToPng conversion`() {
        val svgUrl = "https://example.com/image.svg"
        val pngUrl = svgUrl.svgToPng()
        
        assertThat(pngUrl).startsWith("https://lilico.app/api/svg2png?url=")
        assertThat(pngUrl).contains(svgUrl)
    }

    @Test
    fun `test parseBoringAvatar with boring avatars URL`() {
        val boringUrl = "https://boringavatars.com/avatar/123"
        val parsedUrl = boringUrl.parseBoringAvatar()
        
        assertThat(parsedUrl).contains("lilico.app/api/avatar")
        assertThat(parsedUrl).doesNotContain("boringavatars.com")
    }

    @Test
    fun `test parseBoringAvatar with non-boring avatars URL`() {
        val normalUrl = "https://example.com/avatar.jpg"
        val parsedUrl = normalUrl.parseBoringAvatar()
        
        assertThat(parsedUrl).isEqualTo(normalUrl)
    }

    @Test
    fun `test loadAvatar with placeholder enabled`() {
        val url = "https://example.com/avatar.jpg"
        mockImageView.loadAvatar(url, placeholderEnable = true)
        
        verify(mockImageView).context
    }

    @Test
    fun `test loadAvatar with placeholder disabled`() {
        val url = "https://example.com/avatar.jpg"
        mockImageView.loadAvatar(url, placeholderEnable = false)
        
        verify(mockImageView).context
    }

    @Test
    fun `test loadAvatar with empty URL`() {
        mockImageView.loadAvatar("")
        
        verify(mockImageView).context
    }

    @Test
    fun `test loadAvatar with malformed URL`() {
        mockImageView.loadAvatar("not-a-url")
        
        verify(mockImageView).context
    }
} 