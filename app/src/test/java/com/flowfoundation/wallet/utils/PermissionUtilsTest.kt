package com.flowfoundation.wallet.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.assertj.core.api.Assertions.assertThat

@RunWith(RobolectricTestRunner::class)
class PermissionUtilsTest {

    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mock()
        whenever(mockContext.packageName).thenReturn("com.test.app")
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `test isNotificationPermissionGrand with granted permission on Android 13+`() {
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        whenever(ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS))
            .thenReturn(PackageManager.PERMISSION_GRANTED)

        assertThat(isNotificationPermissionGrand(context)).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `test isNotificationPermissionGrand with denied permission on Android 13+`() {
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        whenever(ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS))
            .thenReturn(PackageManager.PERMISSION_DENIED)

        assertThat(isNotificationPermissionGrand(context)).isFalse()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `test isNotificationPermissionGrand returns true on Android below 13`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThat(isNotificationPermissionGrand(context)).isTrue()
    }

    @Test
    fun `test getNotificationSettingIntent creates correct intent`() {
        val intent = getNotificationSettingIntent(mockContext)
        
        assertThat(intent.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(intent.categories).contains(Intent.CATEGORY_DEFAULT)
        assertThat(intent.data).isEqualTo(Uri.parse("package:com.test.app"))
    }

    @Test
    fun `test getNotificationSettingIntent includes package name in URI`() {
        whenever(mockContext.packageName).thenReturn("com.example.test")
        
        val intent = getNotificationSettingIntent(mockContext)
        
        assertThat(intent.data).isEqualTo(Uri.parse("package:com.example.test"))
    }
} 