package takagi.ru.monica.steam.backup

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.steam.backup.share.MaFileConsentAccount
import takagi.ru.monica.steam.backup.share.MaFileConsentUi
import takagi.ru.monica.steam.backup.share.SteamMaFileConsentScreen
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamMaFileConsentScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun selectionNeverConfirmsAndExplicitConfirmationIsRequired() {
        var confirmations = 0
        val state = mutableStateOf(MaFileConsentUi(busy = false, choosing = true,
            accounts = listOf(MaFileConsentAccount("fixture", "Synthetic account", "Local"))))
        compose.setContent {
            MonicaTheme {
                SteamMaFileConsentScreen(state.value, {}, { state.value = state.value.copy(selectedKey = it) },
                    { confirmations++ }, {})
            }
        }
        val confirm = compose.onNodeWithText(compose.activity.getString(R.string.steam_handoff_confirm))
        confirm.assertIsNotEnabled()
        compose.onNodeWithText("Synthetic account").performClick()
        compose.runOnIdle { assertEquals(0, confirmations) }
        confirm.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, confirmations) }
        if (InstrumentationRegistry.getArguments().getString("handoffScreenshot") == "true") {
            val file = java.io.File(compose.activity.getExternalFilesDir(null), "handoff-consent-preview.png")
            file.outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
