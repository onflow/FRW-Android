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
        whenever(accountManager.userInfo()).doReturn(null)
    }

    @Test
    fun `test meowDomain with valid username`() {
        val username = "testuser"
        val userInfo = UserInfoData(
            username = username,
            nickname = username,
            avatar = "",
            isPrivate = 0,
            created = "0"
        )
        whenever(accountManager.userInfo()).doReturn(userInfo)

        val domain = meowDomain(accountManager)
        assertThat(domain).isEqualTo("$username.${FlowDomainServer.MEOW.domain}")
    }

    @Test
    fun `test meowDomain with null username`() {
        // Return null from userInfo() to simulate null username
        whenever(accountManager.userInfo()).doReturn(null)

        val domain = meowDomain(accountManager)
        assertThat(domain).isNull()
    }

    @Test
    fun `test meowDomain with empty username`() {
        val userInfo = UserInfoData(
            username = "",
            nickname = "test",
            avatar = "",
            isPrivate = 0,
            created = "0"
        )
        whenever(accountManager.userInfo()).doReturn(userInfo)

        val domain = meowDomain(accountManager)
        assertThat(domain).isEqualTo(".${FlowDomainServer.MEOW.domain}")
    }

    @Test
    fun `test meowDomain with null userInfo`() {
        whenever(accountManager.userInfo()).doReturn(null)

        val domain = meowDomain(accountManager)
        assertThat(domain).isNull()
    }

    @Test
    fun `test meowDomainHost with valid username`() {
        val username = "testuser"
        val userInfo = UserInfoData(
            username = username,
            nickname = username,
            avatar = "",
            isPrivate = 0,
            created = "0"
        )
        whenever(accountManager.userInfo()).doReturn(userInfo)

        val host = meowDomainHost(accountManager)
        assertThat(host).isEqualTo(username)
    }

    @Test
    fun `test meowDomainHost with null username`() {
        // Return null from userInfo() to simulate null username
        whenever(accountManager.userInfo()).doReturn(null)

        val host = meowDomainHost(accountManager)
        assertThat(host).isNull()
    }

    @Test
    fun `test meowDomainHost with empty username`() {
        val userInfo = UserInfoData(
            username = "",
            nickname = "test",
            avatar = "",
            isPrivate = 0,
            created = "0"
        )
        whenever(accountManager.userInfo()).doReturn(userInfo)

        val host = meowDomainHost(accountManager)
        assertThat(host).isEqualTo("")
    }

    @Test
    fun `test meowDomainHost with null userInfo`() {
        whenever(accountManager.userInfo()).doReturn(null)

        val host = meowDomainHost(accountManager)
        assertThat(host).isNull()
    }
} 