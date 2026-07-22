package takagi.ru.monica.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import takagi.ru.monica.security.SecurityManager

private val Context.passwordHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "password_history")

/**
 * 密码生成历史管理器
 * 使用 DataStore 存储密码生成历史记录
 */
class PasswordHistoryManager(private val context: Context) {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val securityManager = SecurityManager(context.applicationContext)
    
    companion object {
        private val HISTORY_KEY = stringPreferencesKey("password_generation_history")
        private const val MAX_HISTORY_SIZE = 50 // 最多保存50条历史记录
    }
    
    /**
     * 获取所有历史记录
     */
    val historyFlow: Flow<List<PasswordGenerationHistory>> = context.passwordHistoryDataStore.data
        .onStart { migrateLegacyHistoryIfNeeded() }
        .map { preferences ->
            decodeHistoryPayload(preferences[HISTORY_KEY])
        }
    
    /**
     * 添加新的历史记录
     */
    suspend fun addHistory(
        password: String, 
        packageName: String = "", 
        domain: String = "",
        username: String = "",
        type: String = "AUTOFILL"
    ) {
        context.passwordHistoryDataStore.edit { preferences ->
            val currentHistory = decodeHistoryPayload(preferences[HISTORY_KEY])
            
            // 创建新记录
            val newRecord = PasswordGenerationHistory(
                password = password,
                timestamp = System.currentTimeMillis(),
                packageName = packageName,
                domain = domain,
                username = username,
                type = type
            )
            
            // 添加到列表头部，并限制数量
            val updatedHistory = (listOf(newRecord) + currentHistory).take(MAX_HISTORY_SIZE)
            
            // 保存
            preferences[HISTORY_KEY] = encodeHistoryPayload(updatedHistory)
        }
    }
    
    /**
     * 清空所有历史记录
     */
    suspend fun clearHistory() {
        context.passwordHistoryDataStore.edit { preferences ->
            preferences.remove(HISTORY_KEY)
        }
    }

    /**
     * 导出当前历史为 JSON 字符串
     */
    suspend fun exportHistoryJson(): String {
        val current = historyFlow.first()
        return json.encodeToString(current)
    }

    /**
     * 将历史记录整体导入（替换当前历史）
     */
    suspend fun importHistory(history: List<PasswordGenerationHistory>) {
        context.passwordHistoryDataStore.edit { preferences ->
            preferences[HISTORY_KEY] = encodeHistoryPayload(history.take(MAX_HISTORY_SIZE))
        }
    }
    
    /**
     * 删除指定的历史记录
     */
    suspend fun deleteHistory(timestamp: Long) {
        context.passwordHistoryDataStore.edit { preferences ->
            val currentHistory = decodeHistoryPayload(preferences[HISTORY_KEY])
            
            val updatedHistory = currentHistory.filter { it.timestamp != timestamp }
            preferences[HISTORY_KEY] = encodeHistoryPayload(updatedHistory)
        }
    }

    private suspend fun migrateLegacyHistoryIfNeeded() {
        context.passwordHistoryDataStore.edit { preferences ->
            val raw = preferences[HISTORY_KEY] ?: return@edit
            if (raw.isBlank() || securityManager.looksLikeMonicaCiphertext(raw)) {
                return@edit
            }
            val decoded = runCatching {
                json.decodeFromString<List<PasswordGenerationHistory>>(raw)
            }.getOrNull() ?: return@edit
            preferences[HISTORY_KEY] = encodeHistoryPayload(decoded.take(MAX_HISTORY_SIZE))
        }
    }

    private fun decodeHistoryPayload(raw: String?): List<PasswordGenerationHistory> {
        if (raw.isNullOrBlank()) return emptyList()
        val historyJson = runCatching {
            if (securityManager.looksLikeMonicaCiphertext(raw)) {
                securityManager.decryptData(raw)
            } else {
                raw
            }
        }.getOrDefault("[]")
        return runCatching {
            json.decodeFromString<List<PasswordGenerationHistory>>(historyJson)
        }.getOrDefault(emptyList())
    }

    private fun encodeHistoryPayload(history: List<PasswordGenerationHistory>): String {
        return securityManager.encryptDataLegacyCompat(json.encodeToString(history))
    }
}
