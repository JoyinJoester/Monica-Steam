package takagi.ru.monica.steam.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in export of synthetic UI evidence from secondary users' scoped external storage. */
@RunWith(AndroidJUnit4::class)
class SteamChatEvidenceExportTest {
    @Test fun exportSyntheticEvidence() {
        val label = InstrumentationRegistry.getArguments().getString("chatEvidenceLabel").orEmpty()
        assumeTrue(label.matches(Regex("[a-z0-9-]{1,40}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = requireNotNull(context.getExternalFilesDir(null))
        val output = File(context.filesDir, "chat-ui-export/$label")
        check(!output.exists()) { "Keep earlier evidence intact: $label already exists" }
        assertTrue(output.mkdirs())
        var copied = 0
        listOf("chat-floating-selection", "chat-handoff", "avatar-render-cost.txt", "group-avatar-shots", "inbox-swipe-shots",
            "inbox-interaction", "chat-chrome", "telegram-social-evidence", "edge-contrast.txt").forEach { name ->
            val file = File(source, name)
            if (file.exists()) {
                assertTrue(file.copyRecursively(File(output, name)))
                copied++
            }
        }
        assertTrue("There must be evidence to export", copied > 0)
    }
}
