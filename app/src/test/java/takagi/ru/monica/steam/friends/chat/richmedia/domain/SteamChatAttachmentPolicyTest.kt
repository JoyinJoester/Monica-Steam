package takagi.ru.monica.steam.friends.chat.richmedia.domain

import org.junit.Assert.*
import org.junit.Test

class SteamChatAttachmentPolicyTest {
    @Test fun acceptsEverySteamChatMediaExtensionWithCanonicalMime() {
        val expected = mapOf("jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
            "gif" to "image/gif", "webp" to "image/webp", "avif" to "image/avif", "webm" to "video/webm",
            "mpg" to "video/mpeg", "mpeg" to "video/mpeg", "mp4" to "video/mp4", "ogv" to "video/ogg")
        expected.forEach { (extension, mime) ->
            val result = SteamChatAttachmentPolicy.mediaType("example.$extension", mime)
            assertEquals(extension, mime, result?.mimeType)
            assertEquals(extension, if (mime.startsWith("image/")) SteamChatAttachmentKind.IMAGE else SteamChatAttachmentKind.VIDEO, result?.kind)
            assertTrue(extension, mime in SteamChatAttachmentPolicy.pickerMimeTypes)
        }
    }

    @Test fun rejectsZipAndOtherDocumentsRegardlessOfClaimedMime() {
        for (name in listOf("archive.zip", "archive.ZIP", "document.pdf", "notes.txt", "program.exe", "photo.png.zip", "photo")) {
            for (mime in listOf("application/zip", "application/x-zip-compressed", "image/png", "video/mp4", "application/octet-stream", "")) {
                assertNull("$name / $mime", SteamChatAttachmentPolicy.mediaType(name, mime))
            }
        }
        assertTrue(SteamChatAttachmentPolicy.pickerMimeTypes.none { "zip" in it || "*" in it })
    }

    @Test fun unknownProviderMimeFallsBackOnlyForSupportedExtension() {
        for (mime in listOf("", "application/octet-stream", " APPLICATION/OCTET-STREAM ")) {
            assertEquals("image/png", SteamChatAttachmentPolicy.mediaType("PHOTO.PNG", mime)?.mimeType)
            assertNull(SteamChatAttachmentPolicy.mediaType("archive.zip", mime))
        }
    }

    @Test fun normalizesKnownMimeAliasesAndParameters() {
        assertEquals("image/jpeg", SteamChatAttachmentPolicy.mediaType("photo.jpg", " IMAGE/PJPEG ; name=photo.jpg")?.mimeType)
        assertEquals("image/png", SteamChatAttachmentPolicy.mediaType("photo.png", "image/x-png")?.mimeType)
        assertEquals("video/webm", SteamChatAttachmentPolicy.mediaType("clip.webm", "video/x-webm")?.mimeType)
    }

    @Test fun rejectsMismatchedOrUnsupportedMimeEvenWithMediaSuffix() {
        for (mime in listOf("application/zip", "image/svg+xml", "image/png", "video/mp4")) {
            assertNull(mime, SteamChatAttachmentPolicy.mediaType("photo.jpg", mime))
        }
        assertNull(SteamChatAttachmentPolicy.mediaType("vector.svg", "image/svg+xml"))
        assertNull(SteamChatAttachmentPolicy.mediaType("clip.mov", "video/quicktime"))
    }
}
