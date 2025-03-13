package com.flowfoundation.wallet.utils

import android.content.ContentResolver
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.res.AssetManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.content.Context
import org.junit.After
import org.mockito.Mockito.mockStatic

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], manifest = Config.NONE)
class FileUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockUri: Uri
    private lateinit var mockContext: Context
    private lateinit var mockAssetManager: AssetManager
    private lateinit var mockPackageManager: PackageManager
    private lateinit var testCacheDir: File
    private lateinit var mockedStatic: MockedStatic<Env>

    @Before
    fun setup() {
        mockContentResolver = mock()
        mockUri = mock()
        mockContext = mock()
        mockAssetManager = mock()
        mockPackageManager = mock()

        // Setup test cache directory
        testCacheDir = tempFolder.newFolder("cache")

        // Setup mock context
        whenever(mockContext.cacheDir).thenReturn(testCacheDir)
        whenever(mockContext.contentResolver).thenReturn(mockContentResolver)
        whenever(mockContext.assets).thenReturn(mockAssetManager)
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)

        // Mock PackageManager for FileProvider
        val providerInfo = mock<ProviderInfo>()
        providerInfo.authority = "com.test.authority"
        whenever(mockPackageManager.resolveContentProvider(eq("com.test.authority"), eq(0))).thenReturn(providerInfo)
        
        // Setup static mock for Env
        mockedStatic = mockStatic(Env::class.java)
        mockedStatic.`when`<Context> { Env.getApp() }.thenReturn(mockContext)
    }

    @After
    fun tearDown() {
        // Clean up static mock
        mockedStatic.close()
    }

    @Test
    fun `test Uri toFile with valid input stream`() {
        val testContent = "test content"
        val inputStream = ByteArrayInputStream(testContent.toByteArray())
        whenever(mockContentResolver.openInputStream(mockUri)).thenReturn(inputStream)
        
        val outputFile = tempFolder.newFile("uri_to_file_test.txt")
        val result = mockUri.toFile(outputFile.absolutePath)
        assertThat(result).isNotNull()
        assertThat(result?.exists()).isTrue()
        assertThat(result?.readText()).isEqualTo(testContent)
    }

    @Test
    fun `test Uri toFile with null uri returns null`() {
        val nullUri: Uri? = null
        val outputFile = tempFolder.newFile("null_uri_test.txt")
        val result = nullUri.toFile(outputFile.absolutePath)
        assertThat(result).isNull()
    }

    @Test
    fun `test clearCacheDir deletes all files in cache`() {
        // Create some test files in cache
        val file1 = File(testCacheDir, "cache_test1.txt")
        val file2 = File(testCacheDir, "cache_test2.txt")
        file1.writeText("test1")
        file2.writeText("test2")

        clearCacheDir()

        assertThat(file1.exists()).isFalse()
        assertThat(file2.exists()).isFalse()
    }

    @Test
    fun `test InputStream toFile writes content correctly`() {
        val testContent = "test content"
        val inputStream = ByteArrayInputStream(testContent.toByteArray())
        val outputFile = tempFolder.newFile("input_stream_test.txt")

        inputStream.toFile(outputFile)

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.readText()).isEqualTo(testContent)
    }

    @Test
    fun `test Bitmap saveToFile saves image correctly`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val outputFile = tempFolder.newFile("bitmap_test.jpg")

        bitmap.saveToFile(outputFile)

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0)
    }

    @Test
    fun `test Bitmap saveToFile with PNG format`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val outputFile = tempFolder.newFile("bitmap_png_test.png")

        bitmap.saveToFile(outputFile, CompressFormat.PNG)

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0)
    }

    @Test
    fun `test String saveToFile writes content correctly`() {
        val testContent = "test content"
        val outputFile = tempFolder.newFile("string_save_test.txt")

        testContent.saveToFile(outputFile)

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.readText()).isEqualTo(testContent)
    }

    @Test
    fun `test String saveToFile with null or blank content`() {
        val outputFile = tempFolder.newFile("null_blank_test.txt")
        outputFile.writeText("existing content")

        null.saveToFile(outputFile)
        assertThat(outputFile.readText()).isEqualTo("existing content")

        "".saveToFile(outputFile)
        assertThat(outputFile.readText()).isEqualTo("existing content")
    }

    @Test
    fun `test File read returns content correctly`() {
        val testContent = "test content"
        val testFile = tempFolder.newFile("file_read_test.txt")
        testFile.writeText(testContent)

        val result = testFile.read()

        assertThat(result).isEqualTo(testContent)
    }

    @Test
    fun `test File read with non-existent file returns empty string`() {
        val nonExistentFile = File(tempFolder.root, "nonexistent_read_test.txt")
        val result = nonExistentFile.read()
        assertThat(result).isEmpty()
    }

    @Test
    fun `test readTextFromAssets returns content correctly`() {
        val testContent = "test content"
        whenever(mockAssetManager.open("test.txt")).thenReturn(ByteArrayInputStream(testContent.toByteArray()))

        val result = readTextFromAssets("test.txt")

        assertThat(result).isEqualTo(testContent)
    }

    @Test
    fun `test readTextFromAssets returns null for non-existent file`() {
        whenever(mockAssetManager.open("nonexistent.txt")).thenReturn(null)

        val result = readTextFromAssets("nonexistent.txt")
        assertThat(result).isNull()
    }

    @Test
    fun `test String downloadToGallery launches activity`() {
        val url = "https://example.com/image.jpg"
        val mockLauncher: ActivityResultLauncher<String> = mock()
        
        url.downloadToGallery(mockLauncher)
        
        verify(mockLauncher).launch(argThat { endsWith("image.jpg") })
    }
} 