package com.flowfoundation.wallet.utils

import android.os.Build
import android.widget.ImageView
import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import jp.wasabeef.glide.transformations.BlurTransformation
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito.mockStatic
import org.robolectric.RuntimeEnvironment
import java.net.URLEncoder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], manifest = Config.NONE)
class ImageViewUtilsTest {

    private lateinit var mockImageView: ImageView
    private lateinit var mockRequestManager: RequestManager

    @Before
    fun setup() {
        val realContext = RuntimeEnvironment.getApplication() // Use real Context
        mockImageView = spy(ImageView(RuntimeEnvironment.getApplication()))
        mockImageView.layout(0, 0, 100, 100)

        mockRequestManager = mock()

        // Make sure ImageView returns a real context
        whenever(mockImageView.context).thenReturn(realContext)

        // Correct way to mock Glide.with()
        mockStatic(Glide::class.java).use { mockedGlide ->
            mockedGlide.`when`<RequestManager> { Glide.with(realContext) }
                .thenReturn(mockRequestManager)
        }

        Env.init(realContext)
    }

    @Test
    fun `test loadAvatar with normal URL`() {
        val url = "https://example.com/avatar.jpg"
        mockImageView.loadAvatar(url)

        verify(mockRequestManager).load(any<String>())

    }

    @Test
    fun `test loadAvatar with flovatar URL`() {
        val url = "https://flovatar.com/api/image/123.svg"
        mockImageView.loadAvatar(url)

        verify(mockRequestManager).load(any<String>())

    }

    @Test
    fun `test loadAvatar with transformation`() {
        val url = "https://example.com/avatar.jpg"
        val transformation = BlurTransformation()
        mockImageView.loadAvatar(url, transformation = transformation)

        verify(mockRequestManager).load(any<String>())

    }

    @Test
    fun `test loadAvatar with empty URL`() {
        mockImageView.loadAvatar("")
        verify(mockRequestManager).load(any<String>())

    }

    @Test
    fun `test loadAvatar with invalid URL`() {
        mockImageView.loadAvatar("not a valid url")
        verify(mockRequestManager).load(any<String>())

    }

    @Test
    fun `test svgToPng conversion`() {
        val svgUrl = "https://example.com/image.svg"
        val pngUrl = svgUrl.svgToPng()
        
        assertThat(pngUrl).startsWith("https://lilico.app/api/svg2png?url=")
        assertThat(pngUrl).contains(URLEncoder.encode(svgUrl, "UTF-8"))
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

        verify(mockRequestManager).load(any<String>())

    }

    @Test
    fun `test loadAvatar with placeholder disabled`() {
        val url = "https://example.com/avatar.jpg"
        mockImageView.loadAvatar(url, placeholderEnable = false)

        verify(mockRequestManager).load(any<String>())

    }

    @Test
    fun `test loadAvatar with malformed URL`() {
        mockImageView.loadAvatar("not-a-url")

        verify(mockRequestManager).load(any<String>())

    }


} 