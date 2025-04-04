package com.flowfoundation.wallet.cache

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheManagerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()
    
    private lateinit var cacheManager: TestCacheManager<TestData>
    private lateinit var cacheDir: File

    data class TestData(
        val id: Int,
        val name: String
    )

    @Before
    fun setup() {
        cacheDir = tempFolder.newFolder("cache")
        cacheManager = TestCacheManager("test_cache.json", TestData::class.java, cacheDir)
    }

    @Test
    fun testCacheAndRead() {
        val testData = TestData(1, "test")
        cacheManager.cacheSync(testData)
        
        val result = cacheManager.read()
        assertEquals(testData, result)
    }

    @Test
    fun testClear() {
        val testData = TestData(1, "test")
        cacheManager.cacheSync(testData)
        assertTrue(cacheManager.isCacheExist())
        
        cacheManager.clear()
        Thread.sleep(100) // Wait for IO operation
        assertFalse(cacheManager.isCacheExist())
    }

    @Test
    fun testIsCacheExist() {
        assertFalse(cacheManager.isCacheExist())
        
        val testData = TestData(1, "test")
        cacheManager.cacheSync(testData)
        assertTrue(cacheManager.isCacheExist())
    }

    @Test
    fun testModifyTime() {
        val testData = TestData(1, "test")
        val beforeTime = System.currentTimeMillis()
        Thread.sleep(10) // Ensure some time passes
        
        cacheManager.cacheSync(testData)
        val modifyTime = cacheManager.modifyTime()
        
        assertTrue(modifyTime > beforeTime)
        assertTrue(modifyTime <= System.currentTimeMillis())
    }

    @Test
    fun testIsExpired() {
        val testData = TestData(1, "test")
        cacheManager.cacheSync(testData)
        
        assertFalse(cacheManager.isExpired(1000)) // Not expired after 1 second
        Thread.sleep(50)
        assertFalse(cacheManager.isExpired(100)) // Not expired after 100ms
        assertTrue(cacheManager.isExpired(10)) // Expired after 10ms
    }

    @Test
    fun testReadInvalidJson() {
        val file = File(cacheDir, "test_cache.json")
        file.parentFile?.mkdirs()
        file.writeText("invalid json")
        assertNull(cacheManager.read())
    }

    @Test
    fun testReadEmptyFile() {
        val file = File(cacheDir, "test_cache.json")
        file.parentFile?.mkdirs()
        file.writeText("")
        assertNull(cacheManager.read())
    }

    @Test
    fun testCacheOverwrite() {
        val testData1 = TestData(1, "test1")
        val testData2 = TestData(2, "test2")
        
        cacheManager.cacheSync(testData1)
        assertEquals(testData1, cacheManager.read())
        
        cacheManager.cacheSync(testData2)
        assertEquals(testData2, cacheManager.read())
    }
}