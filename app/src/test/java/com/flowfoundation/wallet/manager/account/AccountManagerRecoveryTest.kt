package com.flowfoundation.wallet.manager.account

import com.flowfoundation.wallet.cache.AccountCacheManager
import com.flowfoundation.wallet.cache.UserPrefixCacheManager
import com.flowfoundation.wallet.manager.app.chainNetWorkString
import com.flowfoundation.wallet.manager.walletdata.FlowWallet
import com.flowfoundation.wallet.network.model.BlockchainData
import com.flowfoundation.wallet.network.model.UserInfoData
import com.flowfoundation.wallet.network.model.WalletData
import com.flowfoundation.wallet.network.model.WalletListData
import io.mockk.every
import io.mockk.firstArg
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class AccountManagerRecoveryTest {
    private val accountSnapshots = mutableListOf<List<Account>>()
    private val prefixSnapshots = mutableListOf<List<UserPrefix>>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(AccountCacheManager, UserPrefixCacheManager)
        every { AccountCacheManager.cache(any()) } answers {
            accountSnapshots += firstArg<List<Account>>().toList()
        }
        every { UserPrefixCacheManager.cache(any()) } answers {
            prefixSnapshots += firstArg<List<UserPrefix>>().toList()
        }
        resetAccountManager()
    }

    @After
    fun tearDown() {
        resetAccountManager()
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun `publishes accumulated recovery while preserving real account identities`() {
        val alice = account(
            uid = "uid-alice",
            username = "alice",
            avatar = "alice.png",
            address = "0x01",
            prefix = null,
            active = true,
        )
        val bob = account(
            uid = "uid-bob",
            username = "bob",
            avatar = "bob.png",
            address = "0x02",
            prefix = "beta",
            active = false,
        )
        seedAccountManager(
            accounts = listOf(alice, bob),
            prefixes = listOf(UserPrefix("uid-alice", "stale-prefix")),
        )
        val publishedLists = mutableListOf<List<Account>>()
        val publishedAccounts = mutableListOf<Account>()
        val accountListener = object : OnAccountUpdate {
            override fun onAccountUpdate(account: Account) {
                publishedAccounts += account
            }
        }
        val listener = object : OnAccountListUpdate {
            override fun onAccountListUpdate(accounts: List<Account>) {
                publishedLists += accounts.toList()
            }
        }
        AccountManager.addListener(accountListener)
        AccountManager.addAccountListUpdateListener(listener)

        val publication = AccountManager.applyRecoveredKeystoreKeys(
            listOf(
                recovery("alpha", "0x01"),
                recovery("beta", "0x02"),
            )
        )

        assertThat(publication.updatedAccountCount).isEqualTo(2)
        assertThat(publication.unresolvedMatches).isEmpty()
        assertThat(AccountManager.list()).hasSize(2)

        val recoveredAlice = AccountManager.list().single { it.wallet?.id == "uid-alice" }
        val recoveredBob = AccountManager.list().single { it.wallet?.id == "uid-bob" }
        assertThat(recoveredAlice).isEqualTo(alice.copy(prefix = "alpha"))
        assertThat(recoveredBob).isEqualTo(bob)
        assertThat(recoveredAlice.userInfo.username).isEqualTo("alice")
        assertThat(recoveredAlice.userInfo.avatar).isEqualTo("alice.png")
        assertThat(recoveredAlice.walletNodes).isEqualTo(alice.walletNodes)

        assertThat(accountSnapshots).containsExactly(AccountManager.list())
        assertThat(prefixSnapshots).containsExactly(
            listOf(
                UserPrefix("uid-alice", "alpha"),
                UserPrefix("uid-bob", "beta"),
            )
        )
        assertThat(publishedAccounts).containsExactly(recoveredAlice)
        assertThat(publishedLists).hasSize(1)
        assertThat(publishedLists.single()).isEqualTo(AccountManager.list())
        verify(exactly = 1) { AccountCacheManager.cache(any()) }
        verify(exactly = 1) { UserPrefixCacheManager.cache(any()) }
    }

    @Test
    fun `repeated recovery is idempotent and wallet UID stays switcher resolvable`() {
        val account = account(
            uid = "real-wallet-uid",
            username = "real-user",
            avatar = "avatar.png",
            address = "0x0a",
            prefix = null,
            active = true,
        )
        seedAccountManager(listOf(account), emptyList())
        val match = recovery("recovered-prefix", "0x0a")

        val first = AccountManager.applyRecoveredKeystoreKeys(listOf(match))
        val second = AccountManager.applyRecoveredKeystoreKeys(listOf(match))

        assertThat(first.updatedAccountCount).isEqualTo(1)
        assertThat(second.updatedAccountCount).isZero()
        assertThat(AccountManager.list()).hasSize(1)
        val switcherAccount = AccountManager.getSwitchAccountList()
            .filterIsInstance<Account>()
            .single { it.wallet?.id == "real-wallet-uid" }
        assertThat(switcherAccount.userInfo.username).isEqualTo("real-user")
        assertThat(switcherAccount.prefix).isEqualTo("recovered-prefix")
        assertThat(prefixSnapshots.single()).containsExactly(
            UserPrefix("real-wallet-uid", "recovered-prefix")
        )
        assertThat(accountSnapshots).hasSize(1)
        assertThat(prefixSnapshots).hasSize(1)
        verify(exactly = 1) { AccountCacheManager.cache(any()) }
        verify(exactly = 1) { UserPrefixCacheManager.cache(any()) }
    }

    @Test
    fun `ambiguous prefixes do not replace a real account`() {
        val account = account(
            uid = "uid-alice",
            username = "alice",
            avatar = "alice.png",
            address = "0x01",
            prefix = null,
            active = true,
        )
        seedAccountManager(listOf(account), emptyList())
        val matches = listOf(
            recovery("alpha", "0x01"),
            recovery("beta", "0x01"),
        )

        val publication = AccountManager.applyRecoveredKeystoreKeys(matches)

        assertThat(publication.updatedAccountCount).isZero()
        assertThat(publication.unresolvedMatches).containsExactlyInAnyOrderElementsOf(matches)
        assertThat(AccountManager.list()).containsExactly(account)
        verify(exactly = 0) { AccountCacheManager.cache(any()) }
        verify(exactly = 0) { UserPrefixCacheManager.cache(any()) }
    }

    @Test
    fun `wallet refresh cannot overwrite a recovered prefix`() {
        val account = account(
            uid = "uid-alice",
            username = "alice",
            avatar = "alice.png",
            address = "0x01",
            prefix = null,
            active = true,
        )
        seedAccountManager(listOf(account), emptyList())
        AccountManager.applyRecoveredKeystoreKeys(listOf(recovery("alpha", "0x01")))

        AccountManager.updateAccountList(listOf(account.copy(prefix = null)))

        assertThat(AccountManager.list().single().prefix).isEqualTo("alpha")
    }

    private fun account(
        uid: String,
        username: String,
        avatar: String,
        address: String,
        prefix: String?,
        active: Boolean,
    ): Account {
        return Account(
            userInfo = UserInfoData(
                username = username,
                nickname = username,
                avatar = avatar,
                created = "1",
            ),
            isActive = active,
            wallet = WalletListData(
                id = uid,
                username = username,
                wallets = listOf(
                    WalletData(
                        name = "Flow",
                        blockchain = listOf(BlockchainData(address, chainNetWorkString())),
                    )
                ),
            ),
            prefix = prefix,
            walletNodes = listOf(
                FlowWallet(
                    address,
                    "Flow",
                    emojiId = 1,
                    chainIdString = chainNetWorkString(),
                )
            ),
        )
    }

    private fun recovery(
        prefix: String,
        address: String,
    ): KeyStoreMigrationManager.RecoveryMatch {
        return KeyStoreMigrationManager.RecoveryMatch(
            prefix = prefix,
            address = address,
            publicKey = "ab".repeat(64),
        )
    }

    private fun seedAccountManager(
        accounts: List<Account>,
        prefixes: List<UserPrefix>,
    ) {
        mutableListField<Account>("accounts").apply {
            clear()
            addAll(accounts)
        }
        mutableListField<UserPrefix>("userPrefixes").apply {
            clear()
            addAll(prefixes)
        }
        currentAccountField().set(AccountManager, accounts.firstOrNull { it.isActive })
    }

    private fun resetAccountManager() {
        mutableListField<Account>("accounts").clear()
        mutableListField<UserPrefix>("userPrefixes").clear()
        mutableListField<Any>("listeners").clear()
        mutableListField<Any>("listListeners").clear()
        currentAccountField().set(AccountManager, null)
        accountSnapshots.clear()
        prefixSnapshots.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> mutableListField(name: String): MutableList<T> {
        val field = AccountManager::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
        return field.get(AccountManager) as MutableList<T>
    }

    private fun currentAccountField() =
        AccountManager::class.java.getDeclaredField("currentAccount").apply {
            isAccessible = true
        }
}
