package takagi.ru.monica.steam.scanner

import android.Manifest
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.scanner.ui.SteamQrScannerScreen
import takagi.ru.monica.ui.theme.MonicaTheme

/** Uses the production entry with synthetic empty local storage, never a Steam account. */
class SteamScannerEntryTest {
    @get:Rule val compose = createAndroidComposeRule<SteamComposeFixtureActivity>()

    @Before fun grantCamera() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName.endsWith(".toolchainpilot"))
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName, Manifest.permission.CAMERA
        )
    }

    @Test fun scannerOpensWithCameraPermissionAndReturns() {
        var backs = 0
        var results = 0
        compose.setContent {
            MonicaTheme {
                Surface {
                    SteamQrScannerScreen(
                        initialAccountId = null,
                        onQrCodeScanned = { _, _ -> results++ },
                        onNavigateBack = { backs++ }
                    )
                }
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.scan_qr_code_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.go_back)).performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertEquals(0, results)
        }
    }
}
