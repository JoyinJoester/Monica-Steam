package takagi.ru.monica.steam.friends.chat.background.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import takagi.ru.monica.MonicaSteamActivity
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationRequest
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle
import takagi.ru.monica.steam.session.domain.SteamAccountSessionOrigin

object SteamChatNotificationContract {
    fun openConversationPendingIntent(
        context: Context,
        handle: SteamAccountSessionHandle,
        partnerSteamId: String
    ): PendingIntent = pendingIntent(context, handle, partnerSteamId)

    fun openChatListPendingIntent(
        context: Context,
        handle: SteamAccountSessionHandle
    ): PendingIntent = pendingIntent(context, handle, partnerSteamId = null)

    fun consume(intent: Intent?): SteamChatNotificationRequest? {
        if (intent?.action != ACTION_OPEN_CHAT) return null
        val request = decode(intent)
        intent.action = null
        EXTRA_KEYS.forEach(intent::removeExtra)
        return request?.takeIf(SteamChatNotificationRequest::isValid)
    }

    private fun pendingIntent(
        context: Context,
        handle: SteamAccountSessionHandle,
        partnerSteamId: String?
    ): PendingIntent {
        val requestKey = "${handle.stableKey}|${partnerSteamId.orEmpty()}"
        val intent = Intent(context, MonicaSteamActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            // PendingIntent equality ignores extras. A full digest also isolates routes
            // whose legacy integer request codes happen to collide.
            data = Uri.Builder().scheme("monica-steam-chat").authority("conversation")
                .appendPath(steamChatConversationNotificationPrefix(handle.stableKey, partnerSteamId.orEmpty()))
                .build()
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ACCOUNT_ID, handle.account.id)
            putExtra(EXTRA_ACCOUNT_STEAM_ID, handle.account.steamId)
            putExtra(EXTRA_PARTNER_STEAM_ID, partnerSteamId)
            when (val source = handle.origin.source) {
                SteamStorageSource.Local -> putExtra(EXTRA_SOURCE_TYPE, SOURCE_LOCAL)
                is SteamStorageSource.Mdbx -> {
                    putExtra(EXTRA_SOURCE_TYPE, SOURCE_MDBX)
                    putExtra(EXTRA_DATABASE_ID, source.databaseId)
                    putExtra(EXTRA_ENTRY_ID, handle.origin.entryId)
                }
            }
        }
        return PendingIntent.getActivity(
            context.applicationContext,
            requestKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun decode(intent: Intent): SteamChatNotificationRequest? = runCatching {
        val origin = when (intent.getStringExtra(EXTRA_SOURCE_TYPE)) {
            SOURCE_LOCAL -> SteamAccountSessionOrigin(SteamStorageSource.Local)
            SOURCE_MDBX -> SteamAccountSessionOrigin(
                source = SteamStorageSource.Mdbx(intent.getLongExtra(EXTRA_DATABASE_ID, 0L)),
                entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
            )
            else -> return null
        }
        SteamChatNotificationRequest(
            origin = origin,
            accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, 0L),
            accountSteamId = intent.getStringExtra(EXTRA_ACCOUNT_STEAM_ID).orEmpty(),
            partnerSteamId = intent.getStringExtra(EXTRA_PARTNER_STEAM_ID)
                ?.takeIf(String::isNotBlank)
        )
    }.getOrNull()

    private const val ACTION_OPEN_CHAT =
        "takagi.ru.monica.steamapp.action.OPEN_STEAM_CHAT"
    private const val EXTRA_SOURCE_TYPE = "steam_chat_source_type"
    private const val EXTRA_DATABASE_ID = "steam_chat_database_id"
    private const val EXTRA_ENTRY_ID = "steam_chat_entry_id"
    private const val EXTRA_ACCOUNT_ID = "steam_chat_account_id"
    private const val EXTRA_ACCOUNT_STEAM_ID = "steam_chat_account_steam_id"
    private const val EXTRA_PARTNER_STEAM_ID = "steam_chat_partner_steam_id"
    private const val SOURCE_LOCAL = "local"
    private const val SOURCE_MDBX = "mdbx"
    private val EXTRA_KEYS = listOf(
        EXTRA_SOURCE_TYPE,
        EXTRA_DATABASE_ID,
        EXTRA_ENTRY_ID,
        EXTRA_ACCOUNT_ID,
        EXTRA_ACCOUNT_STEAM_ID,
        EXTRA_PARTNER_STEAM_ID
    )
}
