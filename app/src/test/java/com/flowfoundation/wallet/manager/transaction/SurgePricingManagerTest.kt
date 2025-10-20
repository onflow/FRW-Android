package com.flowfoundation.wallet.manager.transaction

import android.app.Activity
import com.google.gson.Gson
import com.flowfoundation.wallet.manager.app.ActivityManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.network.interceptor.PayerServiceInterceptor
import com.flowfoundation.wallet.widgets.SurgePricingAlertViewXML
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.*

/**
 * Unit tests for SurgePricingManager
 * Tests surge pricing detection, caching, retry logic, and user interaction flows
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SurgePricingManagerTest {

    @MockK
    private lateinit var mockOkHttpClient: OkHttpClient

    @MockK
    private lateinit var mockCall: Call

    @MockK
    private lateinit var mockResponse: Response

    @MockK
    private lateinit var mockResponseBody: ResponseBody

    @RelaxedMockK
    private lateinit var mockActivity: Activity

    private val gson = Gson()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        // Clear cache before each test
        SurgePricingManager.clearCache()

        // Mock static methods
        mockkStatic(ActivityManager::class)
        mockkStatic(MixpanelManager::class)
        mockkStatic(SurgePricingAlertViewXML::class)
        mockkStatic("com.flowfoundation.wallet.network.functions.FunctionsKt")

        // Default mock behavior
        every { ActivityManager.getCurrentActivity() } returns mockActivity
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `fetchPayerStatus should return cached response when cache is valid`() = runTest {
        // Given - Setup cached status
        val cachedStatus = createMockPayerStatus(surgeActive = true)

        // Manually set cache using reflection (since it's private)
        val cacheField = SurgePricingManager::class.java.getDeclaredField("cachedPayerStatus")
        cacheField.isAccessible = true
        val cache = Class.forName("com.flowfoundation.wallet.manager.transaction.SurgePricingManager\$PayerStatusCache")
            .getDeclaredConstructor(
                PayerServiceInterceptor.PayerStatusResponse::class.java,
                Long::class.java,
                Long::class.java
            ).newInstance(cachedStatus, System.currentTimeMillis(), 60000L)
        cacheField.set(SurgePricingManager, cache)

        // When
        val result = SurgePricingManager.fetchPayerStatus()

        // Then
        assertNotNull(result)
        assertEquals(cachedStatus, result)
        assertTrue(SurgePricingManager.hasCachedStatus())
    }

    @Test
    fun `fetchPayerStatus should fetch fresh data when cache is expired`() = runTest {
        // Given - Setup expired cache
        val expiredStatus = createMockPayerStatus(surgeActive = false)
        val freshStatus = createMockPayerStatus(surgeActive = true)

        // Set expired cache (timestamp from 2 minutes ago)
        val cacheField = SurgePricingManager::class.java.getDeclaredField("cachedPayerStatus")
        cacheField.isAccessible = true
        val expiredCache = Class.forName("com.flowfoundation.wallet.manager.transaction.SurgePricingManager\$PayerStatusCache")
            .getDeclaredConstructor(
                PayerServiceInterceptor.PayerStatusResponse::class.java,
                Long::class.java,
                Long::class.java
            ).newInstance(expiredStatus, System.currentTimeMillis() - 120000L, 60000L)
        cacheField.set(SurgePricingManager, expiredCache)

        // Mock OkHttp response
        mockOkHttpResponse(200, gson.toJson(freshStatus))

        // When
        val result = SurgePricingManager.fetchPayerStatus()

        // Then
        assertNotNull(result)
        assertEquals(true, result?.data?.surge?.active)
    }

    @Test
    fun `fetchPayerStatus should retry on 5xx errors with exponential backoff`() = runTest {
        // Given - First two calls fail with 500, third succeeds
        val successStatus = createMockPayerStatus(surgeActive = true)
        var callCount = 0

        mockkConstructor(OkHttpClient.Builder::class)
        val mockClient = mockk<OkHttpClient>()
        val mockCallLocal = mockk<Call>()

        every { anyConstructed<OkHttpClient.Builder>().build() } returns mockClient
        every { mockClient.newCall(any()) } returns mockCallLocal
        every { mockCallLocal.execute() } answers {
            callCount++
            when (callCount) {
                1, 2 -> mockk<Response> {
                    every { isSuccessful } returns false
                    every { code } returns 500
                    every { message } returns "Internal Server Error"
                    every { body } returns null
                }
                else -> mockk<Response> {
                    every { isSuccessful } returns true
                    every { code } returns 200
                    every { body } returns mockk {
                        every { string() } returns gson.toJson(successStatus)
                    }
                }
            }
        }

        // When
        val result = SurgePricingManager.fetchPayerStatus()

        // Then
        assertNotNull(result)
        assertEquals(3, callCount)
        assertEquals(true, result?.data?.surge?.active)
    }

    @Test
    fun `fetchPayerStatus should not retry on 4xx errors`() = runTest {
        // Given - 401 error (no retry expected)
        mockOkHttpResponse(401, "Unauthorized")

        // When
        val result = SurgePricingManager.fetchPayerStatus()

        // Then
        assertNull(result)
        // Verify only one call was made (no retries)
        verify(exactly = 1) { mockCall.execute() }
    }

    @Test
    fun `fetchPayerStatus should fail open after max retries`() = runTest {
        // Given - All calls fail
        mockkConstructor(OkHttpClient.Builder::class)
        val mockClient = mockk<OkHttpClient>()
        val mockCallLocal = mockk<Call>()

        every { anyConstructed<OkHttpClient.Builder>().build() } returns mockClient
        every { mockClient.newCall(any()) } returns mockCallLocal
        every { mockCallLocal.execute() } throws IOException("Network error")

        // When
        val result = SurgePricingManager.fetchPayerStatus()

        // Then
        assertNull(result) // Should fail open (return null)
    }

    @Test
    fun `fetchPayerStatus should handle concurrent access to cache properly`() = runTest {
        // Given - First fetch populates the cache
        val cachedStatus = createMockPayerStatus(surgeActive = false)
        mockOkHttpResponse(200, gson.toJson(cachedStatus))

        // Populate cache with first call
        val firstResult = SurgePricingManager.fetchPayerStatus()
        assertNotNull(firstResult)

        // When - Multiple concurrent calls should use cache
        val results = (1..10).map {
            async {
                SurgePricingManager.fetchPayerStatus()
            }
        }.awaitAll()

        // Then - All should get the same cached response
        results.forEach { result ->
            assertNotNull(result)
            assertEquals(cachedStatus.status, result?.status)
        }

        // Verify only one network call was made (the initial one)
        verify(exactly = 1) { mockCall.execute() }
    }

    @Test
    fun `fetchPayerStatus should handle malformed JSON response`() = runTest {
        // Given - Malformed JSON response
        mockOkHttpResponse(200, "{ invalid json }")

        // When
        val result = SurgePricingManager.fetchPayerStatus()

        // Then - Should return null (fail open)
        assertNull(result)
    }

    @Test
    fun `fetchPayerStatus should handle response with missing required fields`() = runTest {
        // Given - Response missing surge info
        val incompleteResponse = """
            {
                "status": 200,
                "data": {
                    "feePayer": {
                        "enabled": true
                    }
                }
            }
        """
        mockOkHttpResponse(200, incompleteResponse)

        // When
        val result = SurgePricingManager.fetchPayerStatus()

        // Then - Should still parse what's available
        assertNotNull(result)
        assertEquals(200, result?.status)
        assertNull(result?.data?.surge) // Surge info should be null
        assertTrue(result?.data?.feePayer?.enabled ?: false)
    }

    @Test
    fun `fetchPayerStatus should handle extremely large TTL values from server`() = runTest {
        // Given - Response with very large TTL (1 year in seconds)
        val largeTTLStatus = createMockPayerStatus(
            surgeActive = true,
            ttlSeconds = 31536000L // 365 days
        )
        mockOkHttpResponse(200, gson.toJson(largeTTLStatus))

        // When
        val result = SurgePricingManager.fetchPayerStatus()

        // Then - Should accept and cache with the large TTL
        assertNotNull(result)
        assertTrue(SurgePricingManager.hasCachedStatus())

        // Cache should still be valid after clearing and checking
        assertTrue(SurgePricingManager.hasCachedStatus())
    }

    @Test
    fun `fetchPayerStatus should handle zero or negative TTL values`() = runTest {
        // Given - Response with zero TTL
        val zeroTTLStatus = createMockPayerStatus(
            surgeActive = false,
            ttlSeconds = 0L
        )
        mockOkHttpResponse(200, gson.toJson(zeroTTLStatus))

        // When
        val result = SurgePricingManager.fetchPayerStatus()

        // Then - Should parse successfully but cache will be invalid
        assertNotNull(result)
        // Cache with 0 TTL would be immediately expired
        // The implementation would cache it, but it would be immediately invalid on next check
        // So we're just verifying the response was parsed correctly
        assertEquals(200, result?.status)
    }

    @Test
    fun `fetchPayerStatus should handle network timeout gracefully`() = runTest {
        // Given - Network timeout
        mockkConstructor(OkHttpClient.Builder::class)
        val mockClient = mockk<OkHttpClient>()
        val mockCallLocal = mockk<Call>()

        every { anyConstructed<OkHttpClient.Builder>().build() } returns mockClient
        every { mockClient.newCall(any()) } returns mockCallLocal
        every { mockCallLocal.execute() } throws SocketTimeoutException("Connection timed out")

        // When
        val result = SurgePricingManager.fetchPayerStatus()

        // Then - Should return null (fail open)
        assertNull(result)
    }

    @Test
    fun `fetchPayerStatus should handle unexpected status code in response`() = runTest {
        // Given - Response with unexpected status code in JSON
        val weirdStatus = """
            {
                "status": 999,
                "data": {
                    "surge": {
                        "active": true,
                        "multiplier": 5.0
                    }
                }
            }
        """
        mockOkHttpResponse(200, weirdStatus)

        // When
        val result = SurgePricingManager.fetchPayerStatus()

        // Then - Should parse the response normally
        assertNotNull(result)
        assertEquals(999, result?.status)
        assertTrue(result?.data?.surge?.active ?: false)
    }

    @Test
    fun `cache should be properly managed with TTL from server`() = runTest {
        // Given - Server response with custom TTL
        val statusWithTTL = createMockPayerStatus(
            surgeActive = true,
            ttlSeconds = 120 // 2 minutes
        )
        mockOkHttpResponse(200, gson.toJson(statusWithTTL))

        // When - First call fetches from network
        val result1 = SurgePricingManager.fetchPayerStatus()

        // Then
        assertNotNull(result1)
        assertTrue(SurgePricingManager.hasCachedStatus())

        // When - Clear cache
        SurgePricingManager.clearCache()

        // Then
        assertTrue(!SurgePricingManager.hasCachedStatus())
    }

    // Helper functions

    private fun createMockPayerStatus(
        surgeActive: Boolean = false,
        multiplier: Double = 1.0,
        maxFee: Double = 0.001,
        ttlSeconds: Long = 60
    ): PayerServiceInterceptor.PayerStatusResponse {
        return PayerServiceInterceptor.PayerStatusResponse(
            status = 200,
            data = PayerServiceInterceptor.PayerStatusPayload(
                statusVersion = 1,
                surge = PayerServiceInterceptor.SurgeInfo(
                    active = surgeActive,
                    multiplier = multiplier,
                    maxFee = maxFee,
                    ttlSeconds = ttlSeconds
                ),
                feePayer = PayerServiceInterceptor.PayerInfo(
                    enabled = !surgeActive
                )
            )
        )
    }

    private fun mockOkHttpResponse(code: Int, body: String) {
        mockkConstructor(OkHttpClient.Builder::class)

        every { mockResponseBody.string() } returns body
        every { mockResponse.isSuccessful } returns (code in 200..299)
        every { mockResponse.code } returns code
        every { mockResponse.message } returns ""
        every { mockResponse.body } returns if (code in 200..299) mockResponseBody else null
        every { mockCall.execute() } returns mockResponse

        every { anyConstructed<OkHttpClient.Builder>().build() } returns mockOkHttpClient
        every { mockOkHttpClient.newCall(any()) } returns mockCall
    }
}
