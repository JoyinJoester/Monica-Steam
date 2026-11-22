package takagi.ru.monica.steam.space

import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.SteamSpaceFixtureActivity
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.space.domain.SteamSpaceEvent
import takagi.ru.monica.steam.space.domain.SteamSpaceEventKind
import takagi.ru.monica.steam.space.domain.SteamSpaceGateway
import takagi.ru.monica.steam.space.domain.SteamSpacePage
import takagi.ru.monica.steam.space.presentation.SteamSpaceUiState
import takagi.ru.monica.steam.space.presentation.SteamSpaceViewModel
import takagi.ru.monica.steam.space.ui.SteamSpaceSessionLifecycle

/** Actual Activity stop/start with synthetic transport; never opens the account repository. */
@RunWith(AndroidJUnit4::class)
class SteamSpaceLifecycleUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<SteamSpaceFixtureActivity>? = null
    private var model: SteamSpaceViewModel? = null
    private var selected by mutableStateOf(account())
    private var mounted by mutableStateOf(true)
    private val presenceRefreshes = AtomicInteger()
    private val committedAccountToken = AtomicReference<String?>(null)
    private val requests = Collections.synchronizedList(mutableListOf<SteamAccount>())

    private fun launch(block: (SteamAccount) -> SteamSpacePage = ::page) {
        model = SteamSpaceViewModel(SteamSpaceGateway { account, _, _ ->
            requests += account
            block(account)
        })
        scenario = ActivityScenario.launch(Intent(instrumentation.targetContext, SteamSpaceFixtureActivity::class.java))
        scenario!!.onActivity { activity ->
            activity.setContent {
                val state by model!!.uiState.collectAsState()
                Column {
                    if (mounted) LifecycleFixture(selected)
                    Text("Offline lifecycle fixture")
                    Text(state.accountSteamId.orEmpty())
                }
            }
        }
    }

    @Composable
    private fun LifecycleFixture(account: SteamAccount) {
        SteamSpaceSessionLifecycle(model!!, account) { presenceRefreshes.incrementAndGet() }
        // Observe the exact account passed to the mounted binding, not recomposition of its
        // unchanged parent. The next real gateway request still proves that the model adopted it.
        SideEffect { committedAccountToken.set(account.accessToken) }
    }

    @After fun close() {
        scenario?.close()
        main { model?.deactivate() }
    }

    @Test fun firstActivationRefreshesOnceAndTokenUpdatesDoNotRefreshPresence() {
        launch()
        await("first activation") { model!!.uiState.value.loaded && requests.size == 1 && presenceRefreshes.get() == 1 }
        main { selected = selected.copy(accessToken = "rotated-fixture-token") }
        await("updated account supplied to lifecycle binding") { committedAccountToken.get() == "rotated-fixture-token" }
        instrumentation.waitForIdleSync()
        assertEquals("token updates do not fetch a page", 1, requests.size)
        assertEquals("token updates do not refresh presence", 1, presenceRefreshes.get())
        main { model!!.refresh() }
        await("refresh uses updated credential") { requests.size == 2 && model!!.uiState.value.loaded && !model!!.uiState.value.refreshing }
        assertEquals(listOf("fixture-token", "rotated-fixture-token"), requests.map { it.accessToken })
        assertEquals(1, presenceRefreshes.get())
    }

    @Test fun stopClearsVisibleStateAndStartRestoresCacheWithoutAnotherRequest() {
        launch()
        await("initial data") { model!!.uiState.value.loaded }
        scenario!!.moveToState(Lifecycle.State.CREATED)
        assertEquals(SteamSpaceUiState(), model!!.uiState.value)
        assertEquals(1, requests.size)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        await("same account resumes from cache") { model!!.uiState.value.loaded && presenceRefreshes.get() == 2 }
        assertFalse(model!!.uiState.value.loading)
        assertFalse(model!!.uiState.value.refreshing)
        assertEquals(listOf(ACCOUNT_A), requests.map { it.steamId })
    }

    @Test fun accountChangedWhileStoppedDoesNotStartUntilResumeAndUsesLatestAccount() {
        launch()
        await("initial data") { model!!.uiState.value.loaded }
        scenario!!.moveToState(Lifecycle.State.CREATED)
        main { selected = account(ACCOUNT_B) }
        // Allow any mistakenly scheduled source effect to run while the Activity is stopped.
        SystemClock.sleep(200)
        assertEquals(SteamSpaceUiState(), model!!.uiState.value)
        assertEquals(1, requests.size)
        assertEquals(1, presenceRefreshes.get())
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        await("new account resumes once") {
            model!!.uiState.value.accountSteamId == ACCOUNT_B && model!!.uiState.value.loaded && presenceRefreshes.get() == 2
        }
        assertEquals(listOf(ACCOUNT_A, ACCOUNT_B), requests.map { it.steamId })
    }

    @Test fun foregroundAccountChangeRefreshesOnceAndClearsTheOldFeed() {
        launch()
        await("initial data") { model!!.uiState.value.loaded }
        main { selected = account(ACCOUNT_B) }
        await("new account loaded") {
            model!!.uiState.value.accountSteamId == ACCOUNT_B && model!!.uiState.value.loaded && presenceRefreshes.get() == 2
        }
        assertEquals(listOf(ACCOUNT_A, ACCOUNT_B), requests.map { it.steamId })
        assertEquals(listOf(ACCOUNT_B), model!!.uiState.value.items.map { it.id })
    }

    @Test fun stoppingInterruptsPendingPageAndResumeCanLoadAnotherAccount() {
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        launch { account ->
            if (account.steamId == ACCOUNT_A) {
                entered.countDown()
                try {
                    Thread.sleep(30_000L)
                } finally {
                    stopped.countDown()
                }
            }
            page(account)
        }
        assertTrue("blocking request started", entered.await(5L, TimeUnit.SECONDS))
        scenario!!.moveToState(Lifecycle.State.CREATED)
        assertTrue("ON_STOP interrupted the transport", stopped.await(3L, TimeUnit.SECONDS))
        assertEquals(SteamSpaceUiState(), model!!.uiState.value)
        main { selected = account(ACCOUNT_B) }
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        await("new account after cancellation") { model!!.uiState.value.loaded && model!!.uiState.value.accountSteamId == ACCOUNT_B }
        assertEquals(listOf(ACCOUNT_A, ACCOUNT_B), requests.map { it.steamId })
        assertEquals(listOf(ACCOUNT_B), model!!.uiState.value.items.map { it.id })
        assertNull(model!!.uiState.value.failure)
    }

    @Test fun removingAndRemountingThePageClearsVisibleStateThenRestoresCache() {
        launch()
        await("initial data") { model!!.uiState.value.loaded }
        main { mounted = false }
        await("composition removed") { model!!.uiState.value == SteamSpaceUiState() }
        main { mounted = true }
        await("remounted cached data") { model!!.uiState.value.loaded && presenceRefreshes.get() == 2 }
        assertFalse(model!!.uiState.value.loading)
        assertFalse(model!!.uiState.value.refreshing)
        assertEquals(listOf(ACCOUNT_A), requests.map { it.steamId })
    }

    private fun await(reason: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
        assertTrue("Timed out: $reason", condition())
    }

    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }

    private fun page(account: SteamAccount) = SteamSpacePage(listOf(SteamSpaceEvent(
        id = account.steamId, kind = SteamSpaceEventKind.STATUS,
        actorSteamId = ACCOUNT_B, occurredAtEpochSeconds = 1_794_016_800L,
        body = "Offline lifecycle fixture", sourceUrl = "https://steamcommunity.com/my/home/"
    )), null)

    private fun account(steamId: String = ACCOUNT_A) = SteamAccount(
        id = 901L, steamId = steamId, accountName = "fixture", displayName = "Fixture",
        deviceId = "android:fixture", sharedSecret = "fixture", identitySecret = null,
        revocationCode = null, tokenGid = null, accessToken = "fixture-token", refreshToken = null,
        steamLoginSecure = null, rawSteamGuardJson = "{}", selected = true, sortOrder = 0,
        createdAt = 0L, updatedAt = 0L
    )

    private companion object {
        const val ACCOUNT_A = "76561198000000901"
        const val ACCOUNT_B = "76561198000000902"
    }
}
