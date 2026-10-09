package takagi.ru.monica.steam.session

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.steam.token.ui.SteamTemporaryLoginOption
import takagi.ru.monica.steam.token.ui.SteamLoginImportDialog
import takagi.ru.monica.R
import org.junit.Assert.assertEquals
import takagi.ru.monica.ui.theme.MonicaTheme

class SteamTemporaryLoginUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun temporaryOptionIsAccessibleAndRendersAtDialogWidth() {
        compose.setContent {
            MonicaTheme(darkTheme = false) {
                var temporary by remember { mutableStateOf(false) }
                Box(Modifier.width(280.dp).padding(8.dp)) {
                    SteamTemporaryLoginOption(temporary, { temporary = it })
                }
            }
        }
        compose.onNodeWithTag("temporary_login").assertIsOff().performClick().assertIsOn()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "temporary-login-native.png")
        file.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("temporary_login").performClick().assertIsOff()
    }

    @Test fun passwordDialogSubmitsTemporaryFlagAndClearsItForAuthenticatorMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var submitted: Boolean? = null
        compose.setContent {
            MonicaTheme(darkTheme = false) {
                SteamLoginImportDialog(
                    pendingChallenge = null, availableCodeAccounts = emptyList(), loading = false,
                    onDismissRequest = {},
                    onBeginLogin = { _, _, _, _, temporary -> submitted = temporary },
                    onSubmitLoginCode = {}, allowSessionOnlyMode = true
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.steam_login_account_label)).performTextInput("Test account")
        compose.onNodeWithText(context.getString(R.string.steam_login_password_label)).performTextInput("test-password")
        compose.onNodeWithTag("temporary_login").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText(context.getString(R.string.steam_temporary_login_description)).performScrollTo().assertIsDisplayed()
        compose.mainClock.advanceTimeBy(500)
        val file = File(context.getExternalFilesDir(null), "temporary-login-dialog.png")
        file.outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNode(hasText(context.getString(R.string.steam_temporary_login)) and hasClickAction()
            and !hasTestTag("temporary_login")).performClick()
        compose.runOnIdle { assertEquals(true, submitted) }
        compose.onNodeWithText(context.getString(R.string.steam_login_mode_migrate)).performScrollTo().performClick()
        compose.onNodeWithTag("temporary_login").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.steam_login_mode_session_only)).performClick()
        compose.onNodeWithTag("temporary_login").performScrollTo().assertIsOff()
    }
}
