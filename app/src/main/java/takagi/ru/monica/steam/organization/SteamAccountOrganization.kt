package takagi.ru.monica.steam.organization

import java.util.Locale
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountTags

data class SteamAccountOrganizationFilter(
    val groupName: String? = null,
    val tag: String? = null,
    val pinnedOnly: Boolean = false
) {
    val isActive: Boolean
        get() = pinnedOnly || !groupName.isNullOrBlank() || !tag.isNullOrBlank()
}

data class SteamOrganizationValidation(
    val groupTooLong: Boolean = false,
    val tooManyTags: Boolean = false,
    val tagTooLong: Boolean = false,
    val noteTooLong: Boolean = false
) {
    val isValid: Boolean
        get() = !groupTooLong && !tooManyTags && !tagTooLong && !noteTooLong
}

object SteamAccountOrganizationRules {
    const val MAX_GROUP_LENGTH = 40
    const val MAX_NOTE_LENGTH = 300

    fun parseTags(raw: String): List<String> {
        return SteamAccountTags.normalize(
            raw.split(',', '\n', '，', '、')
        )
    }

    fun validate(groupName: String, rawTags: String, note: String): SteamOrganizationValidation {
        val rawTagsList = rawTags.split(',', '\n', '，', '、')
            .map(String::trim)
            .filter(String::isNotEmpty)
        return SteamOrganizationValidation(
            groupTooLong = groupName.trim().length > MAX_GROUP_LENGTH,
            tooManyTags = rawTagsList.distinctBy { it.lowercase(Locale.ROOT) }.size > SteamAccountTags.MAX_TAGS,
            tagTooLong = rawTagsList.any { it.length > SteamAccountTags.MAX_TAG_LENGTH },
            noteTooLong = note.length > MAX_NOTE_LENGTH
        )
    }

    fun normalizeGroup(groupName: String): String? {
        return groupName.trim().takeIf(String::isNotEmpty)
    }
}

data class SteamAccountAccentOption(
    val key: String,
    val argb: Long
)

object SteamAccountAccentPalette {
    val options = listOf(
        SteamAccountAccentOption("blue", 0xff2563ebL),
        SteamAccountAccentOption("cyan", 0xff0891b2L),
        SteamAccountAccentOption("green", 0xff16a34aL),
        SteamAccountAccentOption("yellow", 0xffca8a04L),
        SteamAccountAccentOption("red", 0xffdc2626L),
        SteamAccountAccentOption("pink", 0xffdb2777L),
        SteamAccountAccentOption("purple", 0xff7c3aedL)
    )
}

object SteamAccountOrganizer {
    fun filter(
        accounts: List<SteamAccount>,
        query: String,
        filter: SteamAccountOrganizationFilter = SteamAccountOrganizationFilter()
    ): List<SteamAccount> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        return accounts.filter { account ->
            val queryMatches = normalizedQuery.isBlank() || listOf(
                account.accountName,
                account.displayName,
                account.steamId,
                account.groupName.orEmpty(),
                account.note,
                account.tags.joinToString(" ")
            ).any { it.lowercase(Locale.ROOT).contains(normalizedQuery) }
            val groupMatches = filter.groupName.isNullOrBlank() ||
                account.groupName.equals(filter.groupName, ignoreCase = true)
            val tagMatches = filter.tag.isNullOrBlank() ||
                account.tags.any { it.equals(filter.tag, ignoreCase = true) }
            queryMatches && groupMatches && tagMatches && (!filter.pinnedOnly || account.pinned)
        }
    }

    fun sortForDisplay(accounts: List<SteamAccount>): List<SteamAccount> {
        return accounts.sortedWith(
            compareByDescending<SteamAccount> { it.pinned }
                .thenBy { it.sortOrder }
                .thenBy { it.id }
        )
    }

    fun groups(accounts: List<SteamAccount>): List<String> {
        return accounts.mapNotNull { it.groupName?.trim()?.takeIf(String::isNotEmpty) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun tags(accounts: List<SteamAccount>): List<String> {
        return accounts.flatMap { it.tags }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
}
