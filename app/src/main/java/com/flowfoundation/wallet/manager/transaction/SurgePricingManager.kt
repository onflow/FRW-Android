package com.flowfoundation.wallet.manager.transaction

import android.app.Activity
import com.google.gson.Gson
import com.flowfoundation.wallet.manager.app.ActivityManager
import com.flowfoundation.wallet.mixpanel.MixpanelManager
import com.flowfoundation.wallet.mixpanel.EVENT_SURGE_PRICING_ACCEPTED
import com.flowfoundation.wallet.mixpanel.EVENT_SURGE_PRICING_DECLINED
import com.flowfoundation.wallet.network.BASE_HOST
import com.flowfoundation.wallet.network.interceptor.HeaderInterceptor
import com.flowfoundation.wallet.network.interceptor.PayerServiceInterceptor
import com.flowfoundation.wallet.network.model.PayerStatusResponse
import com.flowfoundation.wallet.network.model.PayerInfo
import com.flowfoundation.wallet.network.model.BridgePayerInfo
import com.flowfoundation.wallet.network.functions.executeHttpFunction
import com.flowfoundation.wallet.network.model.PayerErrorResponse
import com.flowfoundation.wallet.utils.ioScope
import com.flowfoundation.wallet.utils.logd
import com.flowfoundation.wallet.widgets.SurgePricingAlertViewXML
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Manager class for handling surge pricing logic
 * Encapsulates payer status fetching, caching, and surge pricing alert handling
 */
object SurgePricingManager {
  private const val TAG = "SurgePricingManager"
  private const val DEFAULT_TTL_MS = 60000L // Default 1 minute TTL
  private const val MAX_RETRIES = 3
  private const val CONNECT_TIMEOUT_SECONDS = 5L
  private const val READ_TIMEOUT_SECONDS = 5L

  // Cache for payer status with TTL
  private data class PayerStatusCache(
    val response: PayerStatusResponse,
    val timestamp: Long,
    val ttlMs: Long = DEFAULT_TTL_MS
  ) {
    fun isValid(): Boolean = System.currentTimeMillis() - timestamp < ttlMs
  }

  private var cachedPayerStatus: PayerStatusCache? = null

  /**
   * Fetch payer status from the API with caching and retry logic
   * This is called before transactions to check surge pricing status
   */
  suspend fun fetchPayerStatus(): PayerStatusResponse? {
    return withContext(Dispatchers.IO) {
      // Check cache first
      cachedPayerStatus?.let { cache ->
        if (cache.isValid()) {
          logd(TAG, "Using cached payer status (age: ${System.currentTimeMillis() - cache.timestamp}ms)")
          return@withContext cache.response
        }
      }

      logd(TAG, "Fetching fresh payer status from /api/v1/payer/status")

      // Retry configuration
      var retryCount = 0
      var lastException: Exception? = null

      while (retryCount < MAX_RETRIES) {
        try {
          val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(HeaderInterceptor()) // Add authentication headers
            .addInterceptor(PayerServiceInterceptor())
            .build()

          // Build request for status endpoint
          val request = Request.Builder()
            .url("${BASE_HOST}/api/v1/payer/status")
            .get()
            .build()
          val response = client.newCall(request).execute()
          val responseBody = response.body?.string()

          if (response.isSuccessful && !responseBody.isNullOrBlank()) {
            val payerStatus = Gson().fromJson(responseBody, PayerStatusResponse::class.java)
            logd(TAG, "Payer status fetched successfully:")
            logd(TAG, "  - Status: ${payerStatus.status}")
            logd(TAG, "  - Surge active: ${payerStatus.data?.surge?.active}")
            logd(TAG, "  - Surge multiplier: ${payerStatus.data?.surge?.multiplier}")
            logd(TAG, "  - Max fee: ${payerStatus.data?.surge?.maxFee}")

            // Cache the response with TTL from server or default
            val ttlSeconds = payerStatus.data?.surge?.ttlSeconds ?: 60
            cachedPayerStatus = PayerStatusCache(
              response = payerStatus,
              timestamp = System.currentTimeMillis(),
              ttlMs = ttlSeconds * 1000L
            )

            return@withContext payerStatus
          } else if (response.code in 500..599) {
            // Server error - retry with exponential backoff
            logd(TAG, "Server error (${response.code}) on attempt ${retryCount + 1}, retrying...")
            retryCount++
            if (retryCount < MAX_RETRIES) {
              // Exponential backoff: 1s, 2s, 4s
              val delayMs = (1000L * (1 shl (retryCount - 1))).coerceAtMost(4000L)
              logd(TAG, "Waiting ${delayMs}ms before retry...")
              delay(delayMs)
              continue
            }
          } else {
            // Client error (4xx) or other - don't retry
            logd(TAG, "Failed to fetch payer status: ${response.code} ${response.message}")
            return@withContext null
          }
        } catch (e: Exception) {
          lastException = e
          retryCount++

          if (retryCount < MAX_RETRIES) {
            logd(TAG, "Error fetching payer status (attempt $retryCount): ${e.message}, retrying...")
            // Exponential backoff: 1s, 2s, 4s
            val delayMs = (1000L * (1 shl (retryCount - 1))).coerceAtMost(4000L)
            delay(delayMs)
          } else {
            logd(TAG, "Failed to fetch payer status after $MAX_RETRIES attempts: ${e.message}")
          }
        }
      }

      // Fail open - don't block transactions if status check fails
      logd(TAG, "Payer status check failed after retries, failing open")
      return@withContext null
    }
  }

  /**
   * Helper function to execute payer requests with surge pricing handling
   * Shows alert dialog if surge pricing is detected and waits for user decision
   *
   * @return Response string if successful, null if user cancels or accepts surge pricing (proceed without payer)
   */
  suspend fun executePayerRequestWithSurgeHandling(
    functionName: String,
    data: Any?,
    host: String? = BASE_HOST
  ): String? = suspendCancellableCoroutine { continuation ->
    ioScope {
      try {
        // Execute the HTTP request directly
        val response = executeHttpFunction(functionName, data, host)

        if (response != null) {
          // Success - return the response
          continuation.resume(response)
        } else {
          // Response is null, this could be due to HTTP error (like 429)
          // Check if there's a last error from the interceptor
          val lastError = PayerServiceInterceptor.getLastErrorResponse()

          if (lastError != null && lastError.isSurgePricing()) {
            // Handle 429 surge pricing response
            logd(TAG, "Detected 429 surge pricing response")
            val currentActivity = getCurrentActivity()

            if (currentActivity != null) {
              logd(TAG, "Showing surge pricing alert for 429 response")

              // Get surge info from cached payer status
              val surgeInfo = cachedPayerStatus?.response?.data?.surge
              logd(TAG, "Using cached surge info: active=${surgeInfo?.active}, multiplier=${surgeInfo?.multiplier}, maxFee=${surgeInfo?.maxFee}")

              currentActivity.runOnUiThread {
                SurgePricingAlertViewXML.showSurgeAlert(
                  activity = currentActivity,
                  errorResponse = lastError,
                  surgeInfo = surgeInfo,
                  onUserDecision = { accepted ->
                    if (accepted) {
                      logd(TAG, "User accepted surge pricing - will use user as payer")
                      // Record telemetry for accepting surge
                      MixpanelManager.surgePricingAccepted()
                      SurgePricingAlertViewXML.dismissCurrentAlert()
                      // Return null to indicate proceeding without payer
                      continuation.resume(null)
                    } else {
                      logd(TAG, "User cancelled transaction due to surge pricing")
                      // Record telemetry for declining surge
                      MixpanelManager.surgePricingDeclined()
                      SurgePricingAlertViewXML.dismissCurrentAlert()
                      // Cancel the continuation to interrupt the transaction
                      continuation.cancel()
                    }
                  }
                )
              }
            } else {
              // No activity available, can't show dialog - proceed without payer
              logd(TAG, "No activity available for surge pricing dialog, proceeding without payer")
              continuation.resume(null)
            }
          } else {
            // Other error (network, server error, etc.) - proceed without payer
            logd(TAG, "HTTP request failed (non-surge), proceeding without payer")
            continuation.resume(null)
          }
        }
      } catch (e: Exception) {
        logd(TAG, "Exception during payer request: ${e.message}, proceeding without payer")
        continuation.resume(null)
      }
    }
  }

  /**
   * Get current activity from the ActivityManager
   */
  private fun getCurrentActivity(): Activity? {
    return ActivityManager.getCurrentActivity()
  }

  /**
   * Show surge pricing alert dialog with continuation control
   * This version uses suspendCancellableCoroutine to allow continuation control
   * for interrupting or continuing the flow based on user decision
   *
   * @return true if user accepted, throws CancellationException if user declined
   */
  suspend fun showSurgePricingAlertWithContinuation(): Boolean = suspendCancellableCoroutine { continuation ->
    val currentActivity = getCurrentActivity()

    if (currentActivity == null) {
      logd(TAG, "No activity available for surge pricing dialog")
      // If no activity, we can't show dialog, so we continue without user input
      continuation.resume(false)
      return@suspendCancellableCoroutine
    }

    logd(TAG, "Showing surge pricing alert")

    // Get surge info from cached payer status
    val surgeInfo = cachedPayerStatus?.response?.data?.surge
    logd(TAG, "Using cached surge info: active=${surgeInfo?.active}, multiplier=${surgeInfo?.multiplier}, maxFee=${surgeInfo?.maxFee}")

    // Create a simple error response for the alert (status doesn't matter much here)
    val errorResponse = PayerErrorResponse(status = 429)

    currentActivity.runOnUiThread {
      SurgePricingAlertViewXML.showSurgeAlert(
        activity = currentActivity,
        errorResponse = errorResponse,
        surgeInfo = surgeInfo,
        onUserDecision = { accepted ->
          if (accepted) {
            logd(TAG, "User accepted surge pricing - proceeding")
            MixpanelManager.surgePricingAccepted()
            SurgePricingAlertViewXML.dismissCurrentAlert()
            continuation.resume(true)
          } else {
            logd(TAG, "User declined surge pricing - cancelling")
            MixpanelManager.surgePricingDeclined()
            SurgePricingAlertViewXML.dismissCurrentAlert()
            // Cancel the continuation to interrupt the flow
            continuation.cancel()
          }
        }
      )
    }
  }

  /**
   * Clear the cached payer status
   * Useful for testing or forcing a fresh fetch
   */
  fun clearCache() {
    cachedPayerStatus = null
  }

  /**
   * Check if we have a valid cached status
   */
  fun hasCachedStatus(): Boolean {
    return cachedPayerStatus?.isValid() == true
  }

  /**
   * Get fee payer info if available and conditions allow
   * Returns null if user should pay (surge active or payer unavailable)
   * Returns PayerInfo if we should use fee payer service
   */
  suspend fun getFeePayer(): PayerInfo? {
    val payerStatus = fetchPayerStatus()
    val surge = payerStatus?.data?.surge
    val feePayer = payerStatus?.data?.feePayer

    // If any data is missing, user pays
    val surgeActive = surge?.active
    val payerAvailable = feePayer?.available

    if (surgeActive == null || payerAvailable == null) {
      logd(TAG, "getFeePayer() - Missing data (surge active: $surgeActive, payer available: $payerAvailable), user pays")
      return null
    }

    if (surgeActive) {
      // When surge is active, user pays
      logd(TAG, "getFeePayer() - Surge active, user pays")
      return null
    }

    // When surge is not active, check if payer is available
    val userPay = !payerAvailable
    return if (userPay) {
      logd(TAG, "getFeePayer() - Payer not available, user pays")
      null
    } else {
      logd(TAG, "getFeePayer() - Using fee payer service: ${feePayer.address}")
      feePayer
    }
  }

  /**
   * Get bridge payer info if available and conditions allow
   * Returns null if user should pay (surge active or payer unavailable)
   * Returns BridgePayerInfo if we should use bridge payer service
   */
  suspend fun getBridgePayer(): BridgePayerInfo? {
    val payerStatus = fetchPayerStatus()
    val surge = payerStatus?.data?.surge
    val bridgePayer = payerStatus?.data?.bridgePayer

    // If any data is missing, user pays
    val surgeActive = surge?.active
    val payerAvailable = bridgePayer?.available

    if (surgeActive == null || payerAvailable == null) {
      logd(TAG, "getBridgePayer() - Missing data (surge active: $surgeActive, payer available: $payerAvailable), user pays")
      return null
    }

    if (surgeActive) {
      // When surge is active, user pays
      logd(TAG, "getBridgePayer() - Surge active, user pays")
      return null
    }

    // When surge is not active, check if payer is available
    val userPay = !payerAvailable
    return if (userPay) {
      logd(TAG, "getBridgePayer() - Payer not available, user pays")
      null
    } else {
      logd(TAG, "getBridgePayer() - Using bridge payer service: ${bridgePayer.address}")
      bridgePayer
    }
  }
}
