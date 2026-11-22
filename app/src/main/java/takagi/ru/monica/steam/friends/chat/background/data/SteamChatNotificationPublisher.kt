package takagi.ru.monica.steam.friends.chat.background.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationIdentity
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationPreview
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationPreviewKind
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.data.SteamFriendsPreferencesCache
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle

internal class SteamChatNotificationPublisher(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private val friendsCache = SteamFriendsPreferencesCache(appContext)
    fun canPostMessageNotifications(): Boolean {
        createChannels()
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted || !notificationManager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(MESSAGE_CHANNEL_ID)?.importance ==
                NotificationManager.IMPORTANCE_NONE
            ) {
                return false
            }
        }
        return true
    }

    fun publishIncomingMessage(
        handle: SteamAccountSessionHandle,
        message: SteamChatMessage,
        preview: SteamChatNotificationPreview
    ): Boolean {
        if (!canPostMessageNotifications()) return false
        createChannels()
        val accountName = handle.account.displayName
            .ifBlank { handle.account.accountName }
            .ifBlank { handle.account.steamId }
        val friendName = friendsCache.load(handle.account.steamId)
            ?.friends
            ?.firstOrNull { friend -> friend.steamId == message.partnerSteamId }
            ?.displayName
            .orEmpty()
            .ifBlank { message.partnerSteamId }
        val previewText = preview.displayText(appContext)
        val accountPerson = Person.Builder().setName(accountName).build()
        val friendPerson = Person.Builder()
            .setName(friendName)
            .setKey(message.partnerSteamId)
            .build()
        val timestampMillis = message.timestamp
            .coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
        val address = steamChatMessageNotificationAddress(
            SteamChatNotificationIdentity(
                accountKey = handle.stableKey,
                partnerSteamId = message.partnerSteamId,
                timestamp = message.timestamp,
                ordinal = message.ordinal
            )
        )
        val publicVersion = NotificationCompat.Builder(appContext, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_steam_chat_notification)
            .setContentTitle(appContext.getString(R.string.steam_chat_notification_public_title))
            .setContentText(appContext.getString(R.string.steam_chat_notification_public_text))
            .build()
        val notification = NotificationCompat.Builder(appContext, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_steam_chat_notification)
            .setContentTitle(friendName)
            .setContentText(previewText)
            .setSubText(accountName)
            .setStyle(
                NotificationCompat.MessagingStyle(accountPerson)
                    .addMessage(previewText, timestampMillis, friendPerson)
            )
            .setContentIntent(
                SteamChatNotificationContract.openConversationPendingIntent(
                    appContext,
                    handle,
                    message.partnerSteamId
                )
            )
            .setWhen(timestampMillis)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setGroup(address.groupKey)
            .build()
        return synchronized(messageNotificationLock) {
            runCatching {
                // Android limits active notifications per app. Retire only the oldest chat
                // messages, leaving capacity for other features.
                activeMessageNotifications()
                    .filterNot { it.tag == address.tag && it.id == address.id }
                    .sortedBy(StatusBarNotification::getPostTime)
                    .let { active -> active.take((active.size - MAX_ACTIVE_MESSAGES + 1).coerceAtLeast(0)) }
                    .forEach { notificationManager.cancel(it.tag, it.id) }
                notificationManager.notify(address.tag, address.id, notification)
            }.isSuccess
        }
    }

    fun cancelConversation(accountKey: String, partnerSteamId: String) {
        synchronized(messageNotificationLock) {
            val prefix = steamChatConversationNotificationPrefix(accountKey, partnerSteamId)
            activeMessageNotifications()
                .filter { it.tag?.startsWith(prefix) == true }
                .forEach { notificationManager.cancel(it.tag, it.id) }
            val legacy = steamChatNotificationAddress(accountKey, partnerSteamId)
            notificationManager.cancel(legacy.tag, legacy.id)
        }
    }

    // Query Android instead of tracking notification content in memory, so cancellation
    // still works after a publisher or process is recreated, and after manual dismissal.
    private fun activeMessageNotifications(): List<StatusBarNotification> =
        appContext.getSystemService(NotificationManager::class.java)
            ?.activeNotifications.orEmpty()
            .filter {
                it.tag?.startsWith(MESSAGE_NOTIFICATION_TAG_PREFIX) == true &&
                    it.notification.channelId == MESSAGE_CHANNEL_ID
            }

    fun retireLegacyConnectionNotifications() {
        notificationManager.cancel(LEGACY_SERVICE_NOTIFICATION_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            LEGACY_SERVICE_CHANNEL_IDS.forEach { manager?.deleteNotificationChannel(it) }
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        retireLegacyConnectionNotifications()
        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGE_CHANNEL_ID,
                appContext.getString(R.string.steam_chat_message_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.steam_chat_message_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    private fun SteamChatNotificationPreview.displayText(context: Context): String = when (kind) {
        SteamChatNotificationPreviewKind.TEXT -> text
        SteamChatNotificationPreviewKind.STICKER -> context.getString(
            R.string.steam_chat_notification_sticker,
            text
        )
        SteamChatNotificationPreviewKind.IMAGE -> context.getString(
            R.string.steam_chat_notification_image,
            text
        )
        SteamChatNotificationPreviewKind.VIDEO -> context.getString(
            R.string.steam_chat_notification_video,
            text
        )
        SteamChatNotificationPreviewKind.FILE -> context.getString(
            R.string.steam_chat_notification_file,
            text
        )
        SteamChatNotificationPreviewKind.GAME_INVITE -> text.ifBlank {
            context.getString(R.string.steam_chat_notification_game_invite)
        }
        SteamChatNotificationPreviewKind.STEAM_EVENT -> text.ifBlank {
            context.getString(R.string.steam_chat_notification_steam_event)
        }
    }

    companion object {
        internal const val LEGACY_SERVICE_NOTIFICATION_ID = 887_001
        internal val LEGACY_SERVICE_CHANNEL_IDS = listOf("steam_chat_background_runtime_v2", "steam_chat_background_service")
        private const val MESSAGE_CHANNEL_ID = "steam_chat_messages"
        internal const val MAX_ACTIVE_MESSAGES = 40
        private val messageNotificationLock = Any()
    }

}
