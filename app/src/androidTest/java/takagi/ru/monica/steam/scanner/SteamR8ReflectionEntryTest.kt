package takagi.ru.monica.steam.scanner

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Run against the isolated minified Release APK using the target class loader. */
class SteamR8ReflectionEntryTest {
    @Test fun manifestRegistrarsAndWorkInputMergerRetainPublicConstructors() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        check(target.packageName == "takagi.ru.monica.steamapp.toolchainpilot")
        check(target.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            "Reflection regression requires the minified Release APK"
        }
        val names = listOf(
            "com.google.mlkit.common.internal.CommonComponentRegistrar",
            "com.google.mlkit.vision.barcode.internal.BarcodeRegistrar",
            "com.google.mlkit.vision.common.internal.VisionCommonRegistrar",
            "androidx.work.OverwritingInputMerger"
        )
        names.forEach { name ->
            val type = Class.forName(name, true, target.classLoader)
            assertEquals(type, type.getConstructor().newInstance().javaClass)
            println("R8_REFLECTION_CONSTRUCTOR_OK $name")
        }
    }
}
