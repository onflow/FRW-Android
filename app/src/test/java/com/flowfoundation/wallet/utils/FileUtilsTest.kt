package com.flowfoundation.wallet.utils

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class FileUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockUri: Uri
    private lateinit var testFile: File

    @Before
    fun setup() {
        mockContentResolver = mock()
        mockUri = mock()
        testFile = tempFolder.newFile("test.txt")
        
        // Setup Env singleton with mock context
        val context = ApplicationProvider.getApplicationContext()
        Env.setup(context)
    }

    @Test
    fun `test toContentUri converts file to content uri`() {
        val file = tempFolder.newFile("test.txt")
        val authority = "com.test.authority"
        val uri = file.toContentUri(authority)
        assertThat(uri).isNotNull()
    }

    @Test
    fun `test Uri toFile with valid input stream`() {
        val testContent = "test content"
        val inputStream = ByteArrayInputStream(testContent.toByteArray())
        whenever(mockContentResolver.openInputStream(mockUri)).thenReturn(inputStream)
        
        val context = ApplicationProvider.getApplicationContext()
        context.contentResolver = mockContentResolver
        
        val outputFile = mockUri.toFile(testFile.absolutePath)
        assertThat(outputFile).isNotNull()
        assertThat(outputFile?.exists()).isTrue()
        assertThat(outputFile?.readText()).isEqualTo(testContent)
    }

    @Test
    fun `test Uri toFile with null uri returns null`() {
        val nullUri: Uri? = null
        val result = nullUri.toFile(testFile.absolutePath)
        assertThat(result).isNull()
    }

    @Test
    fun `test clearCacheDir deletes all files in cache`() {
        // Create some test files in cache
        val file1 = File(CACHE_PATH, "test1.txt")
        val file2 = File(CACHE_PATH, "test2.txt")
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
        val outputFile = tempFolder.newFile("output.txt")

        inputStream.toFile(outputFile)

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.readText()).isEqualTo(testContent)
    }

    @Test
    fun `test Bitmap saveToFile saves image correctly`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val outputFile = tempFolder.newFile("test.jpg")

        bitmap.saveToFile(outputFile)

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0)
    }

    @Test
    fun `test Bitmap saveToFile with PNG format`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val outputFile = tempFolder.newFile("test.png")

        bitmap.saveToFile(outputFile, CompressFormat.PNG)

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0)
    }

    @Test
    fun `test String saveToFile writes content correctly`() {
        val testContent = "test content"
        val outputFile = tempFolder.newFile("test.txt")

        testContent.saveToFile(outputFile)

        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.readText()).isEqualTo(testContent)
    }

    @Test
    fun `test String saveToFile with null or blank content`() {
        val outputFile = tempFolder.newFile("test.txt")
        outputFile.writeText("existing content")

        null.saveToFile(outputFile)
        assertThat(outputFile.readText()).isEqualTo("existing content")

        "".saveToFile(outputFile)
        assertThat(outputFile.readText()).isEqualTo("existing content")
    }

    @Test
    fun `test File read returns content correctly`() {
        val testContent = "test content"
        testFile.writeText(testContent)

        val result = testFile.read()

        assertThat(result).isEqualTo(testContent)
    }

    @Test
    fun `test File read with non-existent file returns empty string`() {
        val nonExistentFile = File(tempFolder.root, "nonexistent.txt")
        val result = nonExistentFile.read()
        assertThat(result).isEmpty()
    }

    @Test
    fun `test readTextFromAssets returns content correctly`() {
        // This test requires setting up mock assets in the test context
        val testContent = "test content"
        val assetManager = ApplicationProvider.getApplicationContext().assets
        whenever(assetManager.open(any())).thenReturn(ByteArrayInputStream(testContent.toByteArray()))

        val result = readTextFromAssets("test.txt")

        assertThat(result).isEqualTo(testContent)
    }

    @Test
    fun `test readTextFromAssets returns null for non-existent file`() {
        val result = readTextFromAssets("nonexistent.txt")
        assertThat(result).isNull()
    }

    @Test
    fun `test String downloadToGallery launches activity`() {
        val url = "https://example.com/image.jpg"
        val mockLauncher: androidx.activity.result.ActivityResultLauncher<String> = mock()
        
        url.downloadToGallery(mockLauncher)
        
        verify(mockLauncher).launch(matches { it.endsWith("image.jpg") })
    }
} 