package takagi.ru.monica.steam

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import java.util.Locale

/** Localized Android host, including Popup/Dialog contexts, for synthetic Chinese UI fixtures. */
class SteamChineseComposeFixtureActivity : ComponentActivity() {
    companion object {
        /** Set before fixture recreation so nested Dialog windows inherit the same scale. */
        @Volatile var fontScaleOverride: Float? = null
        @Volatile var densityDpiOverride: Int? = null
    }

    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
            fontScaleOverride?.let { fontScale = it }
            densityDpiOverride?.let { densityDpi = it }
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
}
