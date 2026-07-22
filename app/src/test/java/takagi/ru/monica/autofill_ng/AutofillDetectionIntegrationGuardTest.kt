package takagi.ru.monica.autofill_ng

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillDetectionIntegrationGuardTest {

    @Test
    fun servicePassesManualRequestThroughWeakTargetAndConfidenceGates() {
        val service = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/MonicaAutofillServiceNg.kt"
        ).readText()
        val parser = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/EnhancedAutofillStructureParserV2.kt"
        ).readText()

        assertTrue(service.contains("FillRequest.FLAG_MANUAL_REQUEST"))
        assertTrue(service.contains("allowWeakTargets = isManualRequest"))
        assertTrue(service.contains("manualRequest = isManualRequest"))
        assertTrue(service.contains("if (!isManualRequest && loginTargetCount == 0"))
        assertTrue(parser.contains("if (allowWeakTargets) return@let list"))
    }

    @Test
    fun genericNumbersAndHiddenFieldsUseTheSharedAdmissionPolicy() {
        val parser = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/EnhancedAutofillStructureParserV2.kt"
        ).readText()
        val numberBranch = parser
            .substringAfter("InputType.TYPE_CLASS_NUMBER ->")
            .substringBefore("return out")

        assertTrue(numberBranch.contains("genericNumberFallbackAccuracy()"))
        assertFalse(numberBranch.contains("Accuracy.MEDIUM"))
        assertTrue(parser.contains("shouldIncludeHiddenCredential("))
        assertTrue(parser.contains("matchesUsernameLabel(hint)"))
    }

    private fun projectFile(relativePath: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, relativePath)
    }
}
