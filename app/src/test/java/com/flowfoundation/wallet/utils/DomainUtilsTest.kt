package com.flowfoundation.wallet.utils

import com.flowfoundation.wallet.manager.account.AccountManager
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.page.address.FlowDomainServer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.assertj.core.api.Assertions.assertThat

@RunWith(RobolectricTestRunner::class)
class DomainUtilsTest {

    private val accountManager = mock<AccountManager>()

    @Before
    fun setup() {
        // Reset AccountManager before each test
        AccountManager::class.java.getDeclaredField("userInfo").apply {
            isAccessible = true
            set(null, null)
        }
    }

    @Test
    fun `test meowDomain with valid username`() {
        val username = "testuser"
        val userInfo = UserInfoData(username = username)
        AccountManager::class.java.getDeclaredField("userInfo").apply {
            isAccessible = true
            set(null, userInfo)
        }

        val domain = meowDomain()
        assertThat(domain).isEqualTo("$username.${FlowDomainServer.MEOW.domain}")
    }

    @Test
    fun `test meowDomain with null username`() {
        val userInfo = UserInfoData(username = null)
        AccountManager::class.java.getDeclaredField("userInfo").apply {
            isAccessible = true
            set(null, userInfo)
        }

        val domain = meowDomain()
        assertThat(domain).isNull()
    }

    @Test
    fun `test meowDomain with empty username`() {
        val userInfo = UserInfoData(username = "")
        AccountManager::class.java.getDeclaredField("userInfo").apply {
            isAccessible = true
            set(null, userInfo)
        }

        val domain = meowDomain()
        assertThat(domain).isEqualTo(".${FlowDomainServer.MEOW.domain}")
    }

    @Test
    fun `test meowDomain with null userInfo`() {
        AccountManager::class.java.getDeclaredField("userInfo").apply {
            isAccessible = true
            set(null, null)
        }

        val domain = meowDomain()
        assertThat(domain).isNull()
    }

    @Test
    fun `test meowDomainHost with valid username`() {
        val username = "testuser"
        val userInfo = UserInfoData(username = username)
        AccountManager::class.java.getDeclaredField("userInfo").apply {
            isAccessible = true
            set(null, userInfo)
        }

        val host = meowDomainHost()
        assertThat(host).isEqualTo(username)
    }

    @Test
    fun `test meowDomainHost with null username`() {
        val userInfo = UserInfoData(username = null)
        AccountManager::class.java.getDeclaredField("userInfo").apply {
            isAccessible = true
            set(null, userInfo)
        }

        val host = meowDomainHost()
        assertThat(host).isNull()
    }

    @Test
    fun `test meowDomainHost with empty username`() {
        val userInfo = UserInfoData(username = "")
        AccountManager::class.java.getDeclaredField("userInfo").apply {
            isAccessible = true
            set(null, userInfo)
        }

        val host = meowDomainHost()
        assertThat(host).isEqualTo("")
    }

    @Test
    fun `test meowDomainHost with null userInfo`() {
        AccountManager::class.java.getDeclaredField("userInfo").apply {
            isAccessible = true
            set(null, null)
        }

        val host = meowDomainHost()
        assertThat(host).isNull()
    }
} 