package takagi.ru.monica.steam.friends.chat.richmedia.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatAttachmentUploader
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatUploadException
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatUploadFailure
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatCatalogService
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatCatalogCache
import takagi.ru.monica.steam.friends.chat.richmedia.data.SteamChatCatalogPreferencesCache
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentGateway
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatAttachmentTarget
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatCatalogGateway
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEffect
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatPendingAttachment
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatRichMediaCatalog
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatSticker
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

data class SteamChatRichMediaUiState(
    val emoticons: List<SteamChatEmoticon> = emptyList(),
    val stickers: List<SteamChatSticker> = emptyList(),
    val effects: List<SteamChatEffect> = emptyList(),
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
    private val catalogGateway: SteamChatCatalogGateway,
    private val attachmentGateway: SteamChatAttachmentGateway,
    private val sessionResolver: SteamAccountSessionResolver? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val catalogCache: SteamChatCatalogCache? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamChatRichMediaUiState())
    val uiState: StateFlow<SteamChatRichMediaUiState> = _uiState.asStateFlow()

    private var account: SteamAccount? = null
    private var attachmentTarget: SteamChatAttachmentTarget? = null
    private var catalogGeneration = 0L
    private var catalogJob: Job? = null
    private var catalogAvailable = false
    private var attachmentGeneration = 0L
    private var attachmentPreparationJob: Job? = null
    private var attachmentUploadJob: Job? = null

    fun selectAccount(account: SteamAccount?) {
        if (sameRichMediaAccount(this.account, account)) {
            this.account = account
            return
        }
        val cachedCatalog = account?.let { selected ->
            if (sameRichMediaAccountIdentity(this.account, selected) && catalogAvailable) {
                _uiState.value.let { SteamChatRichMediaCatalog(it.emoticons, it.stickers, it.effects) }
            } else {
                catalogCache?.peek(selected)
            }
        }
        invalidateAttachmentRequests()
        catalogJob?.cancel()
        this.account = account
        catalogGeneration++
        catalogAvailable = cachedCatalog != null
        _uiState.value = SteamChatRichMediaUiState(
            emoticons = cachedCatalog?.emoticons.orEmpty(),
            stickers = cachedCatalog?.stickers.orEmpty(),
            effects = cachedCatalog?.effects.orEmpty(),
            catalogLoading = account != null
        )
        if (account != null) loadCatalogs(account, catalogGeneration)
    }

    fun selectPartner(steamId: String?) {
        selectAttachmentTarget(
            steamId?.takeIf(String::isNotBlank)?.let { SteamChatAttachmentTarget.Friend(it) }
        )
    }

    fun selectGroupRoom(groupId: String?, chatId: String?) {
        selectAttachmentTarget(
            if (!groupId.isNullOrBlank() && !chatId.isNullOrBlank()) {
                SteamChatAttachmentTarget.GroupRoom(groupId, chatId)
            } else {
                null
            }
        )
    }

    private fun selectAttachmentTarget(target: SteamChatAttachmentTarget?) {
        if (attachmentTarget == target) return
        attachmentTarget = target
        clearAttachment()
    }

    fun refreshCatalogs() {
        val current = account ?: return
        if (catalogJob?.isActive == true) return
        catalogGeneration++
        _uiState.update { it.copy(catalogLoading = true, catalogFailure = false) }
        loadCatalogs(current, catalogGeneration)
    }

    fun selectAttachment(rawUri: String) {
        if (rawUri.isBlank() || _uiState.value.attachmentUploading) return
        attachmentPreparationJob?.cancel()
        attachmentGeneration++
        val generation = attachmentGeneration
        _uiState.update {
            it.copy(
                pendingAttachment = null,
                attachmentSpoiler = false,
                attachmentPreparing = true,
                attachmentFailure = null,
                attachmentProgress = 0f
            )
        }
        attachmentPreparationJob = viewModelScope.launch {
            val result = suspendResult {
                withContext(ioDispatcher) { attachmentGateway.inspect(rawUri) }
            }
            if (generation != attachmentGeneration) return@launch
            result.fold(
                onSuccess = { attachment ->
                    _uiState.update {
                        it.copy(
                            pendingAttachment = attachment,
                            attachmentPreparing = false,
                            attachmentFailure = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            pendingAttachment = null,
                            attachmentPreparing = false,
                            attachmentFailure = error.userFacingMessage()
                        )
                    }
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
        val currentTarget = attachmentTarget ?: return
        val attachment = _uiState.value.pendingAttachment ?: return
        if (_uiState.value.attachmentUploading) return
        val spoiler = _uiState.value.attachmentSpoiler
        val generation = attachmentGeneration
        _uiState.update {
            it.copy(
                attachmentUploading = true,
                attachmentProgress = 0f,
                attachmentFailure = null
            )
        }
        attachmentUploadJob?.cancel()
        attachmentUploadJob = viewModelScope.launch {
            val result = suspendResult {
                withContext(ioDispatcher) {
                    uploadAttachmentWithSessionRetry(
                        currentAccount = currentAccount,
                        currentTarget = currentTarget,
                        attachment = attachment,
                        spoiler = spoiler,
                        generation = generation
                    )
                }
            }
            if (!isCurrentAttachmentRequest(generation, currentAccount, currentTarget)) {
                return@launch
            }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            pendingAttachment = null,
                            attachmentSpoiler = false,
                            attachmentUploading = false,
                            attachmentProgress = 0f,
                            attachmentFailure = null,
                            uploadCompletedAt = nowMillis()
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            attachmentUploading = false,
                            attachmentProgress = 0f,
                            attachmentFailure = error.userFacingMessage()
                        )
                    }
                }
            )
        }
    }

    fun clearAttachment() {
        invalidateAttachmentRequests()
        _uiState.update {
            it.copy(
                pendingAttachment = null,
                attachmentSpoiler = false,
                attachmentPreparing = false,
                attachmentUploading = false,
                attachmentProgress = 0f,
                attachmentFailure = null
            )
        }
    }

    fun clearAttachmentFailure() {
        _uiState.value = _uiState.value.copy(attachmentFailure = null)
    }

    private fun loadCatalogs(account: SteamAccount, generation: Long) {
        catalogJob = viewModelScope.launch {
            if (!catalogAvailable) {
                val cached = suspendResult {
                    withContext(ioDispatcher) { catalogCache?.load(account) }
                }.getOrNull()
                if (!isCurrentCatalogRequest(account, generation)) return@launch
                if (cached != null) publishCatalog(cached, refreshing = true)
            }
            val catalogResult = suspendResult {
                withContext(ioDispatcher) {
                    catalogGateway.loadCatalog(sessionResolver.resolveOrKeep(account))
                }
            }
            if (!isCurrentCatalogRequest(account, generation)) return@launch
            val catalog = catalogResult.getOrNull()
            if (catalog != null) {
                // A successful empty response replaces old ownership, too.
                publishCatalog(catalog, refreshing = false)
                suspendResult {
                    withContext(ioDispatcher) { catalogCache?.save(account, catalog) }
                }
            } else {
                // Background failures must not remove usable cached items or flash an error banner.
                _uiState.update {
                    it.copy(catalogLoading = false, catalogFailure = !catalogAvailable)
                }
            }
        }
    }

    private fun publishCatalog(catalog: SteamChatRichMediaCatalog, refreshing: Boolean) {
        catalogAvailable = true
        _uiState.update {
            it.copy(
                emoticons = catalog.emoticons,
                stickers = catalog.stickers,
                effects = catalog.effects,
                catalogLoading = refreshing,
                catalogFailure = false
            )
        }
    }

    private fun isCurrentCatalogRequest(expected: SteamAccount, generation: Long): Boolean =
        generation == catalogGeneration && sameRichMediaAccount(account, expected)

    private fun invalidateAttachmentRequests() {
        attachmentGeneration++
        attachmentPreparationJob?.cancel()
        attachmentPreparationJob = null
        attachmentUploadJob?.cancel()
        attachmentUploadJob = null
    }

    private fun isCurrentAttachmentRequest(
        generation: Long,
        expectedAccount: SteamAccount,
        expectedTarget: SteamChatAttachmentTarget
    ): Boolean = generation == attachmentGeneration &&
        sameRichMediaAccount(account, expectedAccount) &&
        attachmentTarget == expectedTarget

    private suspend fun uploadAttachmentWithSessionRetry(
        currentAccount: SteamAccount,
        currentTarget: SteamChatAttachmentTarget,
        attachment: SteamChatPendingAttachment,
        spoiler: Boolean,
        generation: Long
    ) = try {
        attachmentGateway.upload(
            account = sessionResolver.resolveOrKeep(currentAccount),
            target = currentTarget,
            attachment = attachment,
            spoiler = spoiler,
            onProgress = attachmentProgressCallback(generation, currentAccount, currentTarget)
        )
    } catch (error: SteamChatUploadException) {
        if (!error.isAuthenticationFailure || sessionResolver == null) throw error
        val refreshed = sessionResolver.resolveOrKeep(currentAccount, forceRefresh = true)
        if (refreshed.steamLoginSecure.isNullOrBlank()) {
            throw SteamChatUploadException.authentication("Steam community session expired")
        }
        attachmentGateway.upload(
            account = refreshed,
            target = currentTarget,
            attachment = attachment,
            spoiler = spoiler,
            onProgress = attachmentProgressCallback(generation, currentAccount, currentTarget)
        )
    }

    private fun attachmentProgressCallback(
        generation: Long,
        currentAccount: SteamAccount,
        currentTarget: SteamChatAttachmentTarget
    ): (Float) -> Unit = { progress ->
        if (isCurrentAttachmentRequest(generation, currentAccount, currentTarget)) {
            _uiState.update {
                it.copy(attachmentProgress = progress.coerceIn(0f, 1f))
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamChatRichMediaViewModel(
                        catalogGateway = SteamChatCatalogService(),
                        attachmentGateway = SteamChatAttachmentUploader(appContext),
                        sessionResolver = SteamAccountSourceRepository
                            .get(appContext)
                            .sessionResolver(),
                        catalogCache = SteamChatCatalogPreferencesCache.get(appContext)
                    ) as T
            }
        }
    }
}

private suspend fun <T> suspendResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

private fun sameRichMediaAccount(left: SteamAccount?, right: SteamAccount?): Boolean =
    sameRichMediaAccountIdentity(left, right) &&
        left?.accessToken == right?.accessToken &&
        left?.steamLoginSecure == right?.steamLoginSecure

private fun sameRichMediaAccountIdentity(left: SteamAccount?, right: SteamAccount?): Boolean =
    left?.id == right?.id && left?.steamId == right?.steamId && left?.isTemporary == right?.isTemporary

private fun Throwable.userFacingMessage(): String = message
    .let { raw ->
        when ((this as? SteamChatUploadException)?.failure) {
            SteamChatUploadFailure.LIMITED_ACCOUNT ->
                "Steam 受限账户无法上传图片，请先解除社区受限状态。"
            SteamChatUploadFailure.AUTHENTICATION ->
                "Steam 登录会话已过期，请刷新账号会话后重试。"
            SteamChatUploadFailure.FILE_REJECTED ->
                raw?.takeIf(String::isNotBlank) ?: "Steam 拒绝了这个附件。"
            SteamChatUploadFailure.SERVICE ->
                raw?.takeIf(String::isNotBlank) ?: "Steam 附件服务暂时不可用。"
            SteamChatUploadFailure.UNKNOWN, null -> raw
        }
    }
    ?.takeIf(String::isNotBlank)
    ?.take(240)
    ?: "Steam attachment operation failed"
