package takagi.ru.monica.steam

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Keep
import java.util.Locale

/** Empty host: instrumentation supplies offline data before rendering the production components. */
@Keep
class SteamSpaceFixtureActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) {
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
        }
        super.attachBaseContext(base.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }
}
