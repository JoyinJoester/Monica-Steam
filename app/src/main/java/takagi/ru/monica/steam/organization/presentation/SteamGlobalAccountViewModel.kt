package takagi.ru.monica.steam.organization.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase

data class SteamGlobalAccountUiState(
    val accounts: List<SteamAccount> = emptyList(),
    val selectedAccount: SteamAccount? = null,
    val switchingAccountId: Long? = null
)

class SteamGlobalAccountViewModel(
    private val accountRepository: SteamAccountRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamGlobalAccountUiState())
    val uiState: StateFlow<SteamGlobalAccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.observeAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    selectedAccount = accounts.firstOrNull(SteamAccount::selected)
                        ?: accounts.firstOrNull(),
                    switchingAccountId = null
                )
            }
        }
    }

    fun selectAccount(accountId: Long) {
        val state = _uiState.value
        if (state.switchingAccountId != null || state.selectedAccount?.id == accountId) return
        if (state.accounts.none { account -> account.id == accountId }) return
        _uiState.value = state.copy(switchingAccountId = accountId)
        viewModelScope.launch {
            try {
                accountRepository.select(accountId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(switchingAccountId = null)
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = SteamDatabase.getDatabase(appContext)
                    return SteamGlobalAccountViewModel(
                        accountRepository = SteamAccountRepository(
                            database.steamAccountDao(),
                            SecurityManager(appContext)
                        )
                    ) as T
                }
            }
        }
    }
}
