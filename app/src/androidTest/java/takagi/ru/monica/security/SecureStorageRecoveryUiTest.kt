package takagi.ru.monica.security

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import takagi.ru.monica.ui.screens.SecureStorageRecoveryScreen
import takagi.ru.monica.ui.theme.MonicaTheme

class SecureStorageRecoveryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun passwordRecoveryHandlesFailureWithoutExposingPasswordOrRunningAutomatically() {
        var calls = 0
        compose.setContent {
            MonicaTheme {
                SecureStorageRecoveryScreen({}, {}, {}, onRecover = {
                    calls++
                    throw java.io.IOException("Synthetic failed recovery")
                })
            }
        }
        compose.onNodeWithTag("secure_startup_recover").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithTag("secure_startup_password").performScrollTo().performTextInput("synthetic-password")
        compose.onNodeWithTag("secure_startup_recover").performClick()
        compose.onNodeWithTag("secure_startup_recovery_error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("secure_startup_recover").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, calls) }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "local-recovery-error.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun blockedScreenKeepsActionsReachableWithLargeTextAndDoesNotRunThemAutomatically() {
        var night by mutableStateOf(false)
        var retries = 0
        var copies = 0
        var exits = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MonicaTheme(darkTheme = night) {
                    Box(Modifier.fillMaxSize().testTag("secure_recovery_frame")) {
                        SecureStorageRecoveryScreen({ retries++ }, { copies++ }, { exits++ })
                    }
                }
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night = dark }
            compose.onNodeWithTag("secure_startup_blocked").assertIsDisplayed()
            compose.onNodeWithTag("secure_startup_retry").assertIsDisplayed()
            compose.onNodeWithTag("secure_startup_diagnostic").assertIsDisplayed()
            val bitmap = compose.onNodeWithTag("secure_recovery_frame").captureToImage().asAndroidBitmap()
            File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "secure-startup-${if (dark) "dark" else "light"}.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.runOnIdle { assertEquals(0, retries + copies + exits) }
        compose.onNodeWithTag("secure_startup_retry").performClick()
        compose.onNodeWithTag("secure_startup_diagnostic").performClick()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(1, copies); assertEquals(0, exits) }
    }
}
