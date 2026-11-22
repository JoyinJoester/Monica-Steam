package takagi.ru.monica.steam.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatAttachmentUploader
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatUploadException
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatUploadFailure
import takagi.ru.monica.steam.friends.chat.richmedia.domain.*

@RunWith(AndroidJUnit4::class)
class SteamChatAttachmentPolicyDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun actualUploaderRejectsZipSelectionBeforeOpeningMissingFile() = runBlocking {
        val uploader = SteamChatAttachmentUploader(context)
        try {
            uploader.inspect("file:///nonexistent/monica-synthetic-attachment.zip")
            fail("ZIP must be rejected before opening the stream")
        } catch (error: SteamChatUploadException) {
            assertEquals(SteamChatUploadFailure.UNSUPPORTED_TYPE, error.failure)
        }
    }

    @Test fun actualPngStillPassesInspectionWithSizeAndDimensions() = runBlocking {
        val file = java.io.File.createTempFile("steam-attachment-policy-", ".png", context.cacheDir)
        val bitmap = android.graphics.Bitmap.createBitmap(2, 3, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            val pending = SteamChatAttachmentUploader(context).inspect(android.net.Uri.fromFile(file).toString())
            assertEquals(SteamChatAttachmentKind.IMAGE, pending.kind)
            assertEquals("image/png", pending.mimeType)
            assertEquals(file.length(), pending.sizeBytes)
            assertEquals(2, pending.width)
            assertEquals(3, pending.height)
        } finally {
            bitmap.recycle()
            assertTrue(file.delete())
        }
    }

    @Test fun forgedPendingAttachmentIsRejectedBeforeAnyNetworkRequest() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { requests++; error("Unexpected network request") }.build()
        val uploader = SteamChatAttachmentUploader(context, client)
        val account = SteamAccount(id = 1, steamId = "76561198000000001", accountName = "synthetic", displayName = "Synthetic",
            deviceId = "android:synthetic", sharedSecret = "synthetic", identitySecret = null, revocationCode = null,
            tokenGid = null, accessToken = "synthetic", refreshToken = null, steamLoginSecure = "synthetic",
            rawSteamGuardJson = "{}", selected = true, sortOrder = 0, createdAt = 0, updatedAt = 0)
        val target = SteamChatAttachmentTarget.Friend("76561198000000002")
        val cases = listOf(
            SteamChatPendingAttachment("file:///missing.zip", "archive.zip", "application/zip", 100, SteamChatAttachmentKind.ARCHIVE) to SteamChatUploadFailure.UNSUPPORTED_TYPE,
            SteamChatPendingAttachment("file:///missing.png", "photo.png", "application/zip", 100, SteamChatAttachmentKind.IMAGE) to SteamChatUploadFailure.UNSUPPORTED_TYPE,
            SteamChatPendingAttachment("file:///missing.png", "photo.png", "image/png", 100, SteamChatAttachmentKind.ARCHIVE) to SteamChatUploadFailure.UNSUPPORTED_TYPE,
            SteamChatPendingAttachment("file:///missing.png", "photo.png", "image/png", SteamChatAttachmentUploader.MAX_FILE_BYTES + 1, SteamChatAttachmentKind.IMAGE) to SteamChatUploadFailure.INVALID_SIZE)
        for ((pending, expected) in cases) {
            try {
                uploader.upload(account, target, pending, false) { fail("Rejected attachment cannot report upload progress") }
                fail("Invalid pending attachment must be rejected")
            } catch (error: SteamChatUploadException) {
                assertEquals(expected, error.failure)
            }
        }
        assertEquals(0, requests)
    }
}
