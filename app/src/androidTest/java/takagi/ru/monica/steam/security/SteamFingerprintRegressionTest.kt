package takagi.ru.monica.steam.security

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.steam.SteamBiometricFixtureActivity
import takagi.ru.monica.ui.screens.MasterPasswordLockingSettingsScreen
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.utils.BiometricAuthHelper
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.viewmodel.SettingsViewModel

/** Opt-in host-driven sensor tests. Only the dedicated synthetic installation is allowed. */
class SteamFingerprintRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<SteamBiometricFixtureActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()

    @Before fun requireIsolatedInstallation() {
        check(context.packageName == "takagi.ru.monica.steamapp.fingerprinttest")
        check(args.getString("fingerprintRegression") == "true")
    }

    @Test fun realSystemStatusMatchesBothHelpers() {
        val expected = requireNotNull(args.getString("expectedStatus")).toInt()
        assertEquals(expected, BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
        val available = expected == BiometricManager.BIOMETRIC_SUCCESS
        val basic = BiometricHelper(context)
        val auth = BiometricAuthHelper(context)
        assertEquals(expected, auth.getBiometricStatusCode())
        assertEquals(available, basic.isBiometricAvailable())
        assertEquals(available, basic.hasBiometricEnrolled())
        assertEquals(available, auth.isBiometricAvailable())
        assertEquals(available, auth.isStrongBiometricAvailable())
        assertEquals(available, auth.isBiometricEnrolled())
    }

    @Test fun systemPromptAuthenticates() = checkPrompt("success")

    @Test fun systemPromptCancellationDoesNotAuthenticate() = checkPrompt("cancel")

    @Test fun unrecognizedFingerprintDoesNotAuthenticate() = checkPrompt("invalid")

    private fun checkPrompt(mode: String) {
        assertTrue(BiometricAuthHelper(context).isStrongBiometricAvailable())
        val done = CountDownLatch(1)
        val successes = AtomicInteger()
        val result = AtomicReference<String>()
        fun finish(value: String) { result.set(value); done.countDown() }
        compose.runOnUiThread {
            when (requireNotNull(args.getString("helper"))) {
                "basic" -> BiometricHelper(context).authenticate(
                    compose.activity, title = "Synthetic fingerprint regression",
                    subtitle = "Dedicated test user", negativeButtonText = "Cancel",
                    onSuccess = { successes.incrementAndGet(); finish("success") },
                    onError = { finish("error:$it") }, onFailed = { finish("cancel") }
                )
                "auth" -> BiometricAuthHelper(context).authenticate(
                    compose.activity, title = "Synthetic fingerprint regression",
                    subtitle = "Dedicated test user", description = "No account data",
                    negativeButtonText = "Cancel",
                    onSuccess = { successes.incrementAndGet(); finish("success") },
                    onError = { code, message -> finish("error:$code:$message") },
                    onCancel = { finish("cancel") }
                )
                else -> error("Unknown helper")
            }
        }
        phase("prompt-$mode")
        if (mode == "invalid") {
            assertFalse("An unrecognized finger must not complete authentication",
                done.await(5, TimeUnit.SECONDS))
            assertEquals(0, successes.get())
            phase("cancel-after-invalid")
        }
        assertTrue("Host must complete the actual system prompt", done.await(45, TimeUnit.SECONDS))
        assertEquals(if (mode == "success") "success" else "cancel", result.get())
        assertEquals(if (mode == "success") 1 else 0, successes.get())
    }

    @Test fun settingsShowsRealStatusWithoutClearingSavedPreference() = withSettings { manager ->
        val expected = requireNotNull(args.getString("expectedStatus")).toInt()
        assertEquals(expected, BiometricAuthHelper(context).getBiometricStatusCode())
        val toggle = compose.onAllNodes(isToggleable()).onFirst()
        toggle.assertIsOn()
        if (expected == BiometricManager.BIOMETRIC_SUCCESS) toggle.assertIsEnabled()
        else toggle.assertIsNotEnabled()
        assertTrue(runBlocking { manager.settingsFlow.first().biometricEnabled })
    }

    @Test fun settingsRefreshesAfterSystemEnrollmentWithoutRecreation() = withSettings { manager ->
        val originalActivity = compose.activity
        assertEquals(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricAuthHelper(context).getBiometricStatusCode())
        compose.onAllNodes(isToggleable()).onFirst().assertIsOn().assertIsNotEnabled()
        compose.runOnUiThread {
            originalActivity.startActivity(Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                BiometricManager.Authenticators.BIOMETRIC_STRONG))
        }
        phase("enroll-in-system-settings")
        val deadline = SystemClock.elapsedRealtime() + 150_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (BiometricAuthHelper(context).isStrongBiometricAvailable() &&
                originalActivity.lifecycle.currentState == Lifecycle.State.RESUMED) break
            SystemClock.sleep(100)
        }
        assertTrue(BiometricAuthHelper(context).isStrongBiometricAvailable())
        assertEquals(Lifecycle.State.RESUMED, originalActivity.lifecycle.currentState)
        assertSame(originalActivity, compose.activity)
        compose.waitForIdle()
        compose.onAllNodes(isToggleable()).onFirst().assertIsOn().assertIsEnabled()
        assertTrue(runBlocking { manager.settingsFlow.first().biometricEnabled })
    }

    private fun withSettings(block: (SettingsManager) -> Unit) {
        val manager = SettingsManager(context)
        val previous = runBlocking { manager.settingsFlow.first().biometricEnabled }
        val models = ViewModelStore()
        try {
            runBlocking { manager.updateBiometricEnabled(true) }
            val viewModel = SettingsViewModel(manager)
            models.put("fingerprint-test", viewModel)
            compose.setContent {
                MonicaTheme {
                    MasterPasswordLockingSettingsScreen(viewModel, {}, {}, {})
                }
            }
            compose.waitUntil(10_000) { viewModel.settings.value.biometricEnabled }
            block(manager)
        } finally {
            compose.runOnUiThread { models.clear() }
            runBlocking { manager.updateBiometricEnabled(previous) }
        }
    }

    private fun phase(value: String) = instrumentation.sendStatus(2,
        Bundle().apply { putString("fingerprintPhase", value) })
}
