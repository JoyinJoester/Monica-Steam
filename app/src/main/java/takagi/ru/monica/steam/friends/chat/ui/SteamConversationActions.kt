package takagi.ru.monica.steam.friends.chat.ui

import takagi.ru.monica.steam.friends.chat.data.SteamChatCache
import takagi.ru.monica.steam.friends.chat.info.data.SteamChatInfoPreferencesStore
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationId
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationType
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatCache

/** Everything the inbox can do to a conversation without opening it. */
internal class SteamConversationActions(
    val onTogglePin: (SteamConversationListEntry) -> Unit = {},
    val onToggleArchive: (SteamConversationListEntry) -> Unit = {},
    /** [deleteContent] also clears the messages cached on this device. */
    val onDelete: (List<SteamConversationListEntry>, Boolean) -> Unit = { _, _ -> }
)

internal fun SteamConversationListEntry.preferencesId(
    accountSteamId: String,
    groupAccountSteamId: String
): SteamChatConversationId = SteamChatConversationId(
    accountSteamId = if (type == SteamConversationType.DIRECT) accountSteamId else groupAccountSteamId,
    type = if (type == SteamConversationType.DIRECT) {
        SteamChatConversationType.DIRECT
    } else {
        SteamChatConversationType.GROUP
    },
    peerOrGroupId = id
)

internal fun buildSteamConversationActions(
    store: SteamChatInfoPreferencesStore,
    accountSteamId: String,
    groupAccountSteamId: String,
    chatCache: SteamChatCache,
    groupChatCache: SteamGroupChatCache,
    onChanged: () -> Unit,
    now: () -> Long = { System.currentTimeMillis() / 1_000L }
): SteamConversationActions {
    fun update(
        entry: SteamConversationListEntry,
        transform: (SteamChatConversationPreferences) -> SteamChatConversationPreferences
    ) {
        val id = entry.preferencesId(accountSteamId, groupAccountSteamId)
        store.save(id, transform(store.load(id)))
        onChanged()
    }
    return SteamConversationActions(
        onTogglePin = { entry ->
            update(entry) { it.copy(pinned = !it.pinned) }
        },
        onToggleArchive = { entry ->
            update(entry) { it.copy(archived = !it.archived) }
        },
        onDelete = { entries, deleteContent ->
            val hiddenAt = now()
            entries.forEach { entry ->
                // A deleted chat must not stay pinned or archived behind the scenes, otherwise it
                // would jump straight back into a filtered tab as soon as a message arrives.
                update(entry) {
                    it.copy(pinned = false, archived = false, hiddenAt = hiddenAt)
                }
                if (deleteContent) {
                    if (entry.type == SteamConversationType.DIRECT) {
                        chatCache.removeThread(accountSteamId, entry.id)
                    } else {
                        groupChatCache.removeThread(groupAccountSteamId, entry.id, entry.chatId)
                    }
                }
            }
        }
    )
}
