package takagi.ru.monica.steam.friends.chat.richmedia.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentGateway
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentKind
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentTarget
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatCatalogGateway
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatPendingAttachment
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichMediaCatalog
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatUploadedAttachment
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatUploadException

@OptIn(ExperimentalCoroutinesApi::class)
class SteamChatRichMediaViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun currentFriendImageUploadReportsCompletionOnlyAfterGatewaySuccess() = runTest(scheduler) {
        var uploadedTarget: SteamChatAttachmentTarget? = null
        var uploadedSpoiler = false
        val gateway = FakeAttachmentGateway().apply {
            uploadBlock = { _, target, attachment, spoiler, progress ->
                uploadedTarget = target
                uploadedSpoiler = spoiler
                progress(0.6f)
                SteamChatUploadedAttachment(
                    url = "https://steamusercontent.com/ugc/final.png",
                    label = attachment.displayName,
                    kind = attachment.kind,
                    spoiler = spoiler
                )
            }
        }
        val viewModel = viewModel(gateway)
        viewModel.selectAccount(account(1L, "76561198000000001"))
        viewModel.selectPartner(PARTNER_A)
        runCurrent()

        viewModel.selectAttachment("content://images/1")
        runCurrent()
        assertEquals("photo.png", viewModel.uiState.value.pendingAttachment?.displayName)

        viewModel.setAttachmentSpoiler(true)
        viewModel.uploadAttachment()
        runCurrent()

        assertEquals(SteamChatAttachmentTarget.Friend(PARTNER_A), uploadedTarget)
        assertTrue(uploadedSpoiler)
        assertEquals(123_456L, viewModel.uiState.value.uploadCompletedAt)
        assertNull(viewModel.uiState.value.pendingAttachment)
        assertFalse(viewModel.uiState.value.attachmentUploading)
    }

    @Test
    fun lateUploadCannotCompleteInsideANewFriendConversation() = runTest(scheduler) {
        val uploadGate = CompletableDeferred<SteamChatUploadedAttachment>()
        val gateway = FakeAttachmentGateway().apply {
            uploadBlock = { _, _, _, _, progress ->
                progress(0.4f)
                withContext(NonCancellable) { uploadGate.await() }
            }
        }
        val viewModel = viewModel(gateway)
        viewModel.selectAccount(account(1L, "76561198000000001"))
        viewModel.selectPartner(PARTNER_A)
        runCurrent()
        viewModel.selectAttachment("content://images/1")
        runCurrent()
        viewModel.uploadAttachment()
        runCurrent()
        assertTrue(viewModel.uiState.value.attachmentUploading)

        viewModel.selectPartner(PARTNER_B)
        assertFalse(viewModel.uiState.value.attachmentUploading)
        assertEquals(0L, viewModel.uiState.value.uploadCompletedAt)

        uploadGate.complete(
            SteamChatUploadedAttachment(
                url = "https://steamusercontent.com/ugc/late.png",
                label = "late.png",
                kind = SteamChatAttachmentKind.IMAGE,
                spoiler = false
            )
        )
        runCurrent()

        assertEquals(0L, viewModel.uiState.value.uploadCompletedAt)
        assertNull(viewModel.uiState.value.pendingAttachment)
        assertNull(viewModel.uiState.value.attachmentFailure)
    }

    @Test
    fun groupRoomUploadUsesTheSelectedSteamGroupAndChatIds() = runTest(scheduler) {
        var uploadedTarget: SteamChatAttachmentTarget? = null
        val gateway = FakeAttachmentGateway().apply {
            uploadBlock = { _, target, attachment, spoiler, _ ->
                uploadedTarget = target
                SteamChatUploadedAttachment(
                    url = "https://steamusercontent.com/ugc/group.png",
                    label = attachment.displayName,
                    kind = attachment.kind,
                    spoiler = spoiler
                )
            }
        }
        val viewModel = viewModel(gateway)
        viewModel.selectAccount(account(1L, "76561198000000001"))
        viewModel.selectGroupRoom(GROUP_ID, CHAT_ID)
        runCurrent()
        viewModel.selectAttachment("content://images/group")
        runCurrent()

        viewModel.uploadAttachment()
        runCurrent()

        assertEquals(
            SteamChatAttachmentTarget.GroupRoom(GROUP_ID, CHAT_ID),
            uploadedTarget
        )
        assertEquals(123_456L, viewModel.uiState.value.uploadCompletedAt)
    }

    @Test
    fun lateUploadCannotCompleteInsideANewGroupRoom() = runTest(scheduler) {
        val uploadGate = CompletableDeferred<SteamChatUploadedAttachment>()
        val gateway = FakeAttachmentGateway().apply {
            uploadBlock = { _, _, _, _, progress ->
                progress(0.4f)
                withContext(NonCancellable) { uploadGate.await() }
            }
        }
        val viewModel = viewModel(gateway)
        viewModel.selectAccount(account(1L, "76561198000000001"))
        viewModel.selectGroupRoom(GROUP_ID, CHAT_ID)
        runCurrent()
        viewModel.selectAttachment("content://images/group")
        runCurrent()
        viewModel.uploadAttachment()
        runCurrent()
        assertTrue(viewModel.uiState.value.attachmentUploading)

        viewModel.selectGroupRoom(GROUP_ID_B, CHAT_ID_B)
        assertFalse(viewModel.uiState.value.attachmentUploading)
        assertEquals(0L, viewModel.uiState.value.uploadCompletedAt)

        uploadGate.complete(
            SteamChatUploadedAttachment(
                url = "https://steamusercontent.com/ugc/late-group.png",
                label = "late-group.png",
                kind = SteamChatAttachmentKind.IMAGE,
                spoiler = false
            )
        )
        runCurrent()

        assertEquals(0L, viewModel.uiState.value.uploadCompletedAt)
        assertNull(viewModel.uiState.value.pendingAttachment)
        assertNull(viewModel.uiState.value.attachmentFailure)
    }

    @Test
    fun expiredAttachmentSessionRefreshesAndRetriesExactlyOnce() = runTest(scheduler) {
        val uploadedAccounts = mutableListOf<SteamAccount>()
        val gateway = FakeAttachmentGateway().apply {
            uploadBlock = { account, _, attachment, spoiler, _ ->
                uploadedAccounts += account
                if (uploadedAccounts.size == 1) {
                    throw SteamChatUploadException.authentication("Steam community session expired")
                }
                SteamChatUploadedAttachment(
                    url = "https://steamusercontent.com/ugc/refreshed.png",
                    label = attachment.displayName,
                    kind = attachment.kind,
                    spoiler = spoiler
                )
            }
        }
        val original = account(1L, "76561198000000001")
        val refreshed = original.copy(
            accessToken = "refreshed-token",
            steamLoginSecure = "${original.steamId}||refreshed-token"
        )
        val forceRefreshFlags = mutableListOf<Boolean>()
        val viewModel = SteamChatRichMediaViewModel(
            catalogGateway = SteamChatCatalogGateway { SteamChatRichMediaCatalog() },
            attachmentGateway = gateway,
            sessionResolver = { _, forceRefresh ->
                forceRefreshFlags += forceRefresh
                if (forceRefresh) refreshed else original
            },
            ioDispatcher = dispatcher,
            nowMillis = { 123_456L }
        )
        viewModel.selectAccount(original)
        viewModel.selectPartner(PARTNER_A)
        runCurrent()
        viewModel.selectAttachment("content://images/1")
        runCurrent()

        viewModel.uploadAttachment()
        runCurrent()

        assertEquals(listOf(false, false, true), forceRefreshFlags)
        assertEquals(listOf(original, refreshed), uploadedAccounts)
        assertEquals(123_456L, viewModel.uiState.value.uploadCompletedAt)
        assertNull(viewModel.uiState.value.attachmentFailure)
    }

    @Test
    fun limitedAccountUploadIsNotRetriedAndShowsUsefulChineseMessage() = runTest(scheduler) {
        var uploadCount = 0
        val gateway = FakeAttachmentGateway().apply {
            uploadBlock = { _, _, _, _, _ ->
                uploadCount++
                throw SteamChatUploadException.steamRejected(
                    code = 112,
                    message = "Limited users cannot upload images."
                )
            }
        }
        val forceRefreshFlags = mutableListOf<Boolean>()
        val viewModel = SteamChatRichMediaViewModel(
            catalogGateway = SteamChatCatalogGateway { SteamChatRichMediaCatalog() },
            attachmentGateway = gateway,
            sessionResolver = { account, forceRefresh ->
                forceRefreshFlags += forceRefresh
                account
            },
            ioDispatcher = dispatcher,
            nowMillis = { 123_456L }
        )
        viewModel.selectAccount(account(1L, "76561198000000001"))
        viewModel.selectPartner(PARTNER_A)
        runCurrent()
        viewModel.selectAttachment("content://images/1")
        runCurrent()

        viewModel.uploadAttachment()
        runCurrent()

        assertEquals(1, uploadCount)
        assertEquals(listOf(false, false), forceRefreshFlags)
        assertEquals(
            "Steam 受限账户无法上传图片，请先解除社区受限状态。",
            viewModel.uiState.value.attachmentFailure
        )
        assertEquals(0L, viewModel.uiState.value.uploadCompletedAt)
    }

    @Test
    fun unsupportedSelectionCannotCreatePendingUploadAndExposesLocalizedError() = runTest(scheduler) {
        var uploads = 0
        val gateway = FakeAttachmentGateway().apply {
            inspectBlock = { throw SteamChatUploadException.unsupportedType() }
            uploadBlock = { _, _, _, _, _ -> uploads++; error("Must not upload rejected selection") }
        }
        val viewModel = viewModel(gateway)
        viewModel.selectAccount(account(1L, "76561198000000001"))
        viewModel.selectPartner(PARTNER_A)
        runCurrent()
        viewModel.selectAttachment("content://files/archive.zip")
        runCurrent()
        assertNull(viewModel.uiState.value.pendingAttachment)
        assertFalse(viewModel.uiState.value.attachmentPreparing)
        assertEquals(takagi.ru.monica.R.string.steam_chat_attachment_unsupported_type, viewModel.uiState.value.attachmentFailureResource)
        viewModel.uploadAttachment()
        runCurrent()
        assertEquals(0, uploads)
        assertEquals(0L, viewModel.uiState.value.uploadCompletedAt)
        viewModel.clearAttachmentFailure()
        assertNull(viewModel.uiState.value.attachmentFailureResource)
        assertNull(viewModel.uiState.value.attachmentFailure)
    }

    @Test
    fun serviceAndSessionFailuresUseCorrectResourcesAndRetryPolicy() = runTest(scheduler) {
        for (code in listOf(20, 50)) {
            var uploads = 0
            var forcedRefreshes = 0
            val gateway = FakeAttachmentGateway().apply {
                uploadBlock = { _, _, _, _, _ ->
                    uploads++
                    throw SteamChatUploadException.steamRejected(code, "Synthetic error $code")
                }
            }
            val viewModel = SteamChatRichMediaViewModel(
                catalogGateway = SteamChatCatalogGateway { SteamChatRichMediaCatalog() },
                attachmentGateway = gateway,
                sessionResolver = { account, forceRefresh ->
                    if (forceRefresh) forcedRefreshes++
                    account
                },
                ioDispatcher = dispatcher,
                nowMillis = { 123_456L }
            )
            viewModel.selectAccount(account(1L, "76561198000000001"))
            viewModel.selectPartner(PARTNER_A)
            runCurrent()
            viewModel.selectAttachment("content://images/1")
            runCurrent()
            viewModel.uploadAttachment()
            runCurrent()

            assertEquals(if (code == 20) 1 else 2, uploads)
            assertEquals(if (code == 20) 0 else 1, forcedRefreshes)
            assertEquals(
                if (code == 20) takagi.ru.monica.R.string.steam_chat_attachment_service_failed
                else takagi.ru.monica.R.string.steam_chat_attachment_session_expired,
                viewModel.uiState.value.attachmentFailureResource
            )
            assertFalse(viewModel.uiState.value.attachmentUploading)
            assertEquals(0L, viewModel.uiState.value.uploadCompletedAt)
        }
    }

    private fun viewModel(gateway: SteamChatAttachmentGateway) = SteamChatRichMediaViewModel(
        catalogGateway = SteamChatCatalogGateway { SteamChatRichMediaCatalog() },
        attachmentGateway = gateway,
        ioDispatcher = dispatcher,
        nowMillis = { 123_456L }
    )

    private fun account(id: Long, steamId: String) = SteamAccount(
        id = id,
        steamId = steamId,
        accountName = "account-$id",
        displayName = "Account $id",
        deviceId = "android:$id",
        sharedSecret = "shared",
        identitySecret = null,
        revocationCode = null,
        tokenGid = null,
        accessToken = "token-$id",
        refreshToken = "refresh-$id",
        steamLoginSecure = "$steamId||token-$id",
        rawSteamGuardJson = "{}",
        selected = true,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    private class FakeAttachmentGateway : SteamChatAttachmentGateway {
        var inspectBlock: suspend (String) -> SteamChatPendingAttachment = {
            SteamChatPendingAttachment(
                uri = it,
                displayName = "photo.png",
                mimeType = "image/png",
                sizeBytes = 256L,
                kind = SteamChatAttachmentKind.IMAGE,
                width = 64,
                height = 64
            )
        }
        var uploadBlock: suspend (
            SteamAccount,
            SteamChatAttachmentTarget,
            SteamChatPendingAttachment,
            Boolean,
            (Float) -> Unit
        ) -> SteamChatUploadedAttachment = { _, _, attachment, spoiler, _ ->
            SteamChatUploadedAttachment(
                url = "https://steamusercontent.com/ugc/default.png",
                label = attachment.displayName,
                kind = attachment.kind,
                spoiler = spoiler
            )
        }

        override suspend fun inspect(rawUri: String): SteamChatPendingAttachment =
            inspectBlock(rawUri)

        override suspend fun upload(
            account: SteamAccount,
            target: SteamChatAttachmentTarget,
            attachment: SteamChatPendingAttachment,
            spoiler: Boolean,
            onProgress: (Float) -> Unit
        ): SteamChatUploadedAttachment =
            uploadBlock(account, target, attachment, spoiler, onProgress)
    }

    private companion object {
        const val PARTNER_A = "76561198000000002"
        const val PARTNER_B = "76561198000000003"
        const val GROUP_ID = "123456789012345678"
        const val CHAT_ID = "987654321098765432"
        const val GROUP_ID_B = "223456789012345678"
        const val CHAT_ID_B = "887654321098765432"
    }
}
