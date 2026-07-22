package takagi.ru.monica.steam.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamPlusPaymentFlowRegressionGuardTest {

    @Test
    fun settingsPlusOpensPaymentInsteadOfActivatingImmediately() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()
        val plusBranch = source
            .substringAfter("SteamSettingsChild.PLUS ->")
            .substringBefore("SteamSettingsChild.PAYMENT ->")

        assertTrue(
            "The Plus CTA must navigate to the payment child screen.",
            plusBranch.contains("onNavigateToPayment = { child = SteamSettingsChild.PAYMENT }")
        )
        assertFalse(
            "The settings Plus CTA must not activate the license before payment.",
            plusBranch.contains("updatePlusActivated(true)")
        )
    }

    @Test
    fun paymentPageKeepsMonicaDonationMethodsAndBottomActivation() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/PaymentScreen.kt"
        ).readText()

        assertTrue(source.contains("support_author_qr"))
        assertTrue(source.contains("https://ko-fi.com/joyinjoester"))
        assertTrue(source.contains("https://afdian.com/a/JoyinJoester/plan"))
        assertTrue(source.contains("verticalScroll(rememberScrollState())"))
        assertTrue(source.indexOf("Payment Links Card") < source.indexOf("Activation Card"))
        assertTrue(source.contains("onActivatePlus()"))
    }

    @Test
    fun steamPaymentPageUsesDockSafeModifier() {
        val paymentSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/PaymentScreen.kt"
        ).readText()
        val settingsSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MonicaSteamSettingsScreen.kt"
        ).readText()

        assertTrue(paymentSource.contains("modifier: Modifier = Modifier"))
        assertTrue(paymentSource.contains("Scaffold(\n        modifier = modifier"))
        assertTrue(
            settingsSource.substringAfter("SteamSettingsChild.PAYMENT ->")
                .substringBefore("SteamSettingsChild.DEVELOPER ->")
                .contains("modifier = modifier")
        )
    }

    private fun projectFile(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("Unable to find project file: $relativePath")
    }
}
