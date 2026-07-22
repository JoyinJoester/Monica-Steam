package takagi.ru.monica.attachments.executor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeePassAttachmentExecutorRegressionGuardTest {

    @Test
    fun keepassAttachmentOperationsDoNotRequireWarmServiceCache() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/attachments/executor/KeePassAttachmentExecutor.kt"
        ).readText()

        assertFalse(
            "KeePass attachments must not gate upload/download/delete on cache-only unlock state. " +
                "The executor may be backed by a different service instance than the one that opened the database.",
            source.contains("!kdbxService.isDatabaseUnlocked(databaseId)")
        )
        assertTrue(
            "Invalid KeePass credentials should still surface as the attachment locked error.",
            source.contains("KeePassErrorCode.INVALID_CREDENTIAL") &&
                source.contains("AttachmentError.KdbxLocked")
        )
        assertTrue(
            "KeePass attachment executor must resolve the active service at operation time, " +
                "because Compose may keep an older facade instance after service registration.",
            source.contains("kdbxServiceProvider: () -> KeePassKdbxService") &&
                source.contains("get() = kdbxServiceProvider()")
        )

        val localKeePassViewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/LocalKeePassViewModel.kt"
        ).readText()
        assertTrue(
            "The user-facing KeePass ViewModel should register its active service for attachment reuse.",
            localKeePassViewModel.contains("AttachmentContainer.registerKeePassService(kdbxService)")
        )

        val attachmentContainer = projectFile(
            "app/src/main/java/takagi/ru/monica/attachments/AttachmentContainer.kt"
        ).readText()
        assertTrue(
            "AttachmentContainer should pass a service provider so remembered facades see the latest KeePass service.",
            attachmentContainer.contains("kdbxServiceProvider = { keepassService(app) }") &&
                attachmentContainer.contains("private fun keepassService(app: Context): KeePassKdbxService")
        )
    }

    @Test
    fun pendingDraftsCanFlushToKeePassAfterNewEntryIsSaved() {
        val editSection = projectFile(
            "app/src/main/java/takagi/ru/monica/attachments/ui/AttachmentsEditSection.kt"
        ).readText()
        assertTrue(
            "Draft attachment flush must accept a KeePass target instead of always writing LOCAL attachments.",
            editSection.contains("attachmentSource: AttachmentSource = AttachmentSource.LOCAL") &&
                editSection.contains("keepassContext: AttachmentFacade.KeePassContext? = null") &&
                editSection.contains("source = attachmentSource") &&
                editSection.contains("keepassContext = keepassContext")
        )

        val addEditScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/AddEditPasswordScreen.kt"
        ).readText()
        assertTrue(
            "New KeePass password saves must resolve the generated entry UUID before flushing pending attachments.",
            addEditScreen.contains("val savedEntry = viewModel.getPasswordEntryById(firstPasswordId)") &&
                addEditScreen.contains("val draftKeePassContext = savedEntry?.let") &&
                addEditScreen.contains("AttachmentSource.KEEPASS") &&
                addEditScreen.contains("keepassContext = draftKeePassContext")
        )
    }

    private fun projectFile(relativePath: String): File {
        val candidates = mutableListOf<File>()
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            candidates += File(dir, relativePath)
            dir = dir.parentFile
        }

        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to find project file: $relativePath from ${System.getProperty("user.dir")}")
    }
}
