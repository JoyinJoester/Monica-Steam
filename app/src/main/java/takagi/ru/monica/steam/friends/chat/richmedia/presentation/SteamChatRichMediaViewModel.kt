package takagi.ru.monica.steam.friends.chat.richmedia.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatAttachmentUploader
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatCatalogService
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatPendingAttachment
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatSticker
import takagi.ru.monica.steam.network.SteamSessionRefreshService

data class SteamChatRichMediaUiState(
    val emoticons: List<SteamChatEmoticon> = emptyList(),
    val stickers: List<SteamChatSticker> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogFailure: Boolean = false,
    val pendingAttachment: SteamChatPendingAttachment? = null,
    val attachmentSpoiler: Boolean = false,
    val attachmentPreparing: Boolean = false,
    val attachmentUploading: Boolean = false,
    val attachmentProgress: Float = 0f,
    val attachmentFailure: String? = null,
    val uploadCompletedAt: Long = 0L
)

class SteamChatRichMediaViewModel(
    private val catalogService: SteamChatCatalogService,
    private val attachmentUploader: SteamChatAttachmentUploader,
    private val sessionRefreshService: SteamSessionRefreshService? = SteamSessionRefreshService(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamChatRichMediaUiState())
    val uiState: StateFlow<SteamChatRichMediaUiState> = _uiState.asStateFlow()

    private var account: SteamAccount? = null
    private var partnerSteamId: String? = null
    private var catalogGeneration = 0L

    fun selectAccount(account: SteamAccount?) {
        if (this.account?.id == account?.id && this.account?.accessToken == account?.accessToken) {
            this.account = account
            return
        }
        this.account = account
        catalogGeneration++
        _uiState.value = SteamChatRichMediaUiState(catalogLoading = account != null)
        if (account != null) loadCatalogs(account, catalogGeneration)
    }

    fun selectPartner(steamId: String?) {
        if (partnerSteamId == steamId) return
        partnerSteamId = steamId
        clearAttachment()
    }

    fun refreshCatalogs() {
        val current = account ?: return
        catalogGeneration++
        _uiState.value = _uiState.value.copy(catalogLoading = true, catalogFailure = false)
        loadCatalogs(current, catalogGeneration)
    }

    fun selectAttachment(rawUri: String) {
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return
        _uiState.value = _uiState.value.copy(
            attachmentPreparing = true,
            attachmentFailure = null,
            attachmentProgress = 0f
        )
        viewModelScope.launch {
            val result = runCatching { withContext(ioDispatcher) { attachmentUploader.inspect(uri) } }
            result.fold(
                onSuccess = { attachment ->
                    _uiState.value = _uiState.value.copy(
                        pendingAttachment = attachment,
                        attachmentPreparing = false,
                        attachmentFailure = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        pendingAttachment = null,
                        attachmentPreparing = false,
                        attachmentFailure = error.userFacingMessage()
                    )
                }
            )
        }
    }

    fun setAttachmentSpoiler(spoiler: Boolean) {
        if (_uiState.value.attachmentUploading) return
        _uiState.value = _uiState.value.copy(attachmentSpoiler = spoiler)
    }

    fun uploadAttachment() {
        val currentAccount = account ?: return
        val currentPartner = partnerSteamId ?: return
        val attachment = _uiState.value.pendingAttachment ?: return
        if (_uiState.value.attachmentUploading) return
        val spoiler = _uiState.value.attachmentSpoiler
        _uiState.value = _uiState.value.copy(
            attachmentUploading = true,
            attachmentProgress = 0f,
            attachmentFailure = null
        )
        viewModelScope.launch {
            val result = runCatching {
                withContext(ioDispatcher) {
                    attachmentUploader.upload(
                        account = prepareRichMediaSession(currentAccount, sessionRefreshService),
                        partnerSteamId = currentPartner,
                        attachment = attachment,
                        spoiler = spoiler,
                        onProgress = { progress ->
                            _uiState.value = _uiState.value.copy(attachmentProgress = progress)
                        }
                    )
                }
            }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        pendingAttachment = null,
                        attachmentSpoiler = false,
                        attachmentUploading = false,
                        attachmentProgress = 0f,
                        attachmentFailure = null,
                        uploadCompletedAt = nowMillis()
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        attachmentUploading = false,
                        attachmentProgress = 0f,
                        attachmentFailure = error.userFacingMessage()
                    )
                }
            )
        }
    }

    fun clearAttachment() {
        _uiState.value = _uiState.value.copy(
            pendingAttachment = null,
            attachmentSpoiler = false,
            attachmentPreparing = false,
            attachmentUploading = false,
            attachmentProgress = 0f,
            attachmentFailure = null
        )
    }

    fun clearAttachmentFailure() {
        _uiState.value = _uiState.value.copy(attachmentFailure = null)
    }

    private fun loadCatalogs(account: SteamAccount, generation: Long) {
        viewModelScope.launch {
            val emoticons = async(ioDispatcher) {
                runCatching {
                    catalogService.loadEmoticons(prepareRichMediaSession(account, sessionRefreshService))
                }
            }
            val stickers = async(ioDispatcher) { runCatching { catalogService.loadStickers() } }
            val emoticonResult = emoticons.await()
            val stickerResult = stickers.await()
            if (generation != catalogGeneration || this@SteamChatRichMediaViewModel.account?.id != account.id) {
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                emoticons = emoticonResult.getOrDefault(emptyList()),
                stickers = stickerResult.getOrDefault(emptyList()),
                catalogLoading = false,
                catalogFailure = emoticonResult.isFailure && stickerResult.isFailure
            )
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamChatRichMediaViewModel(
                        catalogService = SteamChatCatalogService(),
                        attachmentUploader = SteamChatAttachmentUploader(appContext)
                    ) as T
            }
        }
    }
}

private fun prepareRichMediaSession(
    account: SteamAccount,
    service: SteamSessionRefreshService?
): SteamAccount {
    val refreshed = service?.refreshIfNeeded(account)
    val accessToken = refreshed?.accessToken ?: account.accessToken
    val secureCookie = account.steamLoginSecure?.takeIf(String::isNotBlank)
        ?: accessToken?.takeIf(String::isNotBlank)?.let { "${account.steamId}||$it" }
    if (refreshed == null && secureCookie == account.steamLoginSecure) return account
    return account.copy(
        accessToken = accessToken,
        refreshToken = refreshed?.refreshToken ?: account.refreshToken,
        steamLoginSecure = accessToken?.takeIf(String::isNotBlank)?.let {
            "${account.steamId}||$it"
        } ?: secureCookie
    )
}

private fun Throwable.userFacingMessage(): String = message
    ?.takeIf(String::isNotBlank)
    ?.take(240)
    ?: "Steam attachment operation failed"
