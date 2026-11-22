package takagi.ru.monica.steam.space.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep
import takagi.ru.monica.steam.space.data.SteamSpacePostService
import takagi.ru.monica.steam.space.domain.SteamSpacePostGateway
import takagi.ru.monica.steam.space.domain.isValidSteamSpacePost

enum class SteamSpaceComposePhase { EDITING, SENDING, SUCCEEDED, UNKNOWN }
data class SteamSpaceComposeState(
    val accountId: Long? = null,
    val steamId: String? = null,
    val text: String = "",
    val phase: SteamSpaceComposePhase = SteamSpaceComposePhase.EDITING,
    val preparationFailed: Boolean = false
) {
    val canSubmit get() = accountId != null && phase == SteamSpaceComposePhase.EDITING && isValidSteamSpacePost(text)
}

/** Draft stays in memory. A lost response freezes resubmission until the user checks Steam. */
class SteamSpaceComposeViewModel(
    private val gateway: SteamSpacePostGateway,
    private val resolver: SteamAccountSessionResolver? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = 45_000L
) : ViewModel() {
    private val _state = MutableStateFlow(SteamSpaceComposeState())
    val state = _state.asStateFlow()
    private var account: SteamAccount? = null
    private var generation = 0L
    private var job: Job? = null
    private var dispatched = false

    fun selectAccount(value: SteamAccount?) {
        if (value?.id == account?.id && value?.steamId == account?.steamId &&
            value?.isTemporary == account?.isTemporary && value?.createdAt == account?.createdAt) {
            account = value
            return
        }
        clear()
        if (value?.hasRealSteamId == true) {
            account = value
            _state.value = SteamSpaceComposeState(value.id, value.steamId)
        }
    }

    fun updateText(text: String) {
        if (_state.value.phase == SteamSpaceComposePhase.EDITING)
            _state.value = _state.value.copy(text = text, preparationFailed = false)
    }

    fun publish() {
        val selected = account ?: return
        val draft = _state.value
        if (!draft.canSubmit) return
        val ticket = ++generation
        dispatched = false
        _state.value = draft.copy(phase = SteamSpaceComposePhase.SENDING, preparationFailed = false)
        val work = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val prepared = withTimeout(timeoutMillis) {
                    withContext(dispatcher) { resolver.resolveOrKeep(selected, forceRefresh = false) }
                }
                ensureCurrent(selected, ticket)
                check(prepared.id == selected.id && prepared.steamId == selected.steamId) { "Account changed during preparation" }
                check(prepared.hasRealSteamId && !prepared.accessToken.isNullOrBlank()) { "A Steam session is required" }
                // Conservatively mark dispatch before handing control to the blocking worker.
                dispatched = true
                withTimeout(timeoutMillis) { runInterruptible(dispatcher) { gateway.publish(prepared, draft.text) } }
                ensureCurrent(selected, ticket)
                _state.value = _state.value.copy(phase = SteamSpaceComposePhase.SUCCEEDED)
            } catch (cancelled: CancellationException) {
                if (current(selected, ticket)) failCurrent()
                throw cancelled
            } catch (_: Exception) {
                if (current(selected, ticket)) failCurrent()
            }
        }
        job = work
        work.start()
    }

    private fun failCurrent() {
        _state.value = _state.value.copy(
            phase = if (dispatched) SteamSpaceComposePhase.UNKNOWN else SteamSpaceComposePhase.EDITING,
            preparationFailed = !dispatched
        )
    }

    /** Backgrounding cancels local waiting; a sent post cannot be retracted by cancellation. */
    fun stop() {
        ++generation
        job?.cancel()
        job = null
        if (_state.value.phase == SteamSpaceComposePhase.SENDING) failCurrent()
    }

    fun clear() {
        stop()
        account = null
        dispatched = false
        _state.value = SteamSpaceComposeState()
    }

    private fun current(selected: SteamAccount, ticket: Long) = generation == ticket &&
        account?.id == selected.id && account?.steamId == selected.steamId
    private suspend fun ensureCurrent(selected: SteamAccount, ticket: Long) {
        currentCoroutineContext().ensureActive()
        if (!current(selected, ticket)) throw CancellationException("Compose account changed")
    }

    override fun onCleared() { clear(); super.onCleared() }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val accounts = SteamAccountSourceRepository.get(context.applicationContext)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SteamSpaceComposeViewModel(SteamSpacePostService(), accounts.sessionResolver()) as T
            }
        }
    }
}
