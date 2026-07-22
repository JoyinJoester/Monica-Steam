package takagi.ru.monica.notes.domain

import android.content.Context
import takagi.ru.monica.security.SecurityManager

interface NoteDraftStorage {
    fun saveDraft(noteId: Long, title: String, content: String, tagsText: String)
    fun loadDraft(noteId: Long): NoteDraftStore.NoteDraft?
    fun clearDraft(noteId: Long)
    fun hasDraft(noteId: Long): Boolean
}

class NoteDraftStore(context: Context) : NoteDraftStorage {

    companion object {
        @Volatile
        private var instance: NoteDraftStore? = null

        fun init(context: Context): NoteDraftStore {
            return instance ?: synchronized(this) {
                instance ?: NoteDraftStore(context.applicationContext).also { instance = it }
            }
        }

        fun get(): NoteDraftStore = instance
            ?: throw IllegalStateException("NoteDraftStore not initialized. Call init(context) first.")
    }

    data class NoteDraft(
        val title: String,
        val content: String,
        val tagsText: String
    )

    private val legacyPrefs = context.applicationContext
        .getSharedPreferences("note_drafts", Context.MODE_PRIVATE)
    private val securityManager = SecurityManager(context.applicationContext)

    override fun saveDraft(noteId: Long, title: String, content: String, tagsText: String) {
        if (title.isBlank() && content.isBlank() && tagsText.isBlank()) {
            clearDraft(noteId)
            return
        }
        securityManager.putProtectedString(key(noteId, "title"), title)
        securityManager.putProtectedString(key(noteId, "content"), content)
        securityManager.putProtectedString(key(noteId, "tags"), tagsText)
        legacyPrefs.edit()
            .remove(key(noteId, "title"))
            .remove(key(noteId, "content"))
            .remove(key(noteId, "tags"))
            .remove(key(noteId, "ts"))
            .apply()
    }

    override fun loadDraft(noteId: Long): NoteDraft? {
        val protectedContent = securityManager.getProtectedString(key(noteId, "content"))
        if (protectedContent != null) {
            val title = securityManager.getProtectedString(key(noteId, "title")).orEmpty()
            val tags = securityManager.getProtectedString(key(noteId, "tags")).orEmpty()
            return NoteDraft(title = title, content = protectedContent, tagsText = tags)
        }

        if (!legacyPrefs.contains(key(noteId, "content"))) return null
        val content = legacyPrefs.getString(key(noteId, "content"), null) ?: return null
        val title = legacyPrefs.getString(key(noteId, "title"), "") ?: ""
        val tags = legacyPrefs.getString(key(noteId, "tags"), "") ?: ""
        saveDraft(noteId, title, content, tags)
        return NoteDraft(title = title, content = content, tagsText = tags)
    }

    override fun clearDraft(noteId: Long) {
        securityManager.removeProtectedString(key(noteId, "title"))
        securityManager.removeProtectedString(key(noteId, "content"))
        securityManager.removeProtectedString(key(noteId, "tags"))
        legacyPrefs.edit()
            .remove(key(noteId, "title"))
            .remove(key(noteId, "content"))
            .remove(key(noteId, "tags"))
            .remove(key(noteId, "ts"))
            .apply()
    }

    override fun hasDraft(noteId: Long): Boolean {
        return securityManager.getProtectedString(key(noteId, "content")) != null ||
            legacyPrefs.contains(key(noteId, "content"))
    }

    private fun key(noteId: Long, field: String): String = "$noteId.$field"
}
