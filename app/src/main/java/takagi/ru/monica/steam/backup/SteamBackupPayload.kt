package takagi.ru.monica.steam.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.importer.SteamMaFileBackupCodec

@Serializable
data class SteamBackupPayload(
    val schema: Int = 1,
    val createdAt: Long,
    val accounts: List<SteamBackupAccount>
)

@Serializable
data class SteamBackupAccount(
    val steamId: String,
    val accountName: String,
    val displayName: String,
    val deviceId: String,
    val sharedSecret: String,
    val identitySecret: String? = null,
    val revocationCode: String? = null,
    val tokenGid: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val steamLoginSecure: String? = null,
    val maFileJson: String,
    val groupName: String? = null,
    val tags: List<String> = emptyList(),
    val accentArgb: Long? = null,
    val note: String = "",
    val pinned: Boolean = false,
    val selected: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long
) {
    companion object {
        fun fromAccount(account: SteamAccount): SteamBackupAccount {
            return SteamBackupAccount(
                steamId = account.steamId,
                accountName = account.accountName,
                displayName = account.displayName,
                deviceId = account.deviceId,
                sharedSecret = account.sharedSecret,
                identitySecret = account.identitySecret,
                revocationCode = account.revocationCode,
                tokenGid = account.tokenGid,
                accessToken = account.accessToken,
                refreshToken = account.refreshToken,
                steamLoginSecure = account.steamLoginSecure,
                maFileJson = SteamMaFileBackupCodec.encode(account),
                groupName = account.groupName,
                tags = account.tags,
                accentArgb = account.accentArgb,
                note = account.note,
                pinned = account.pinned,
                selected = account.selected,
                sortOrder = account.sortOrder,
                createdAt = account.createdAt
            )
        }
    }
}

object SteamBackupPayloadCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun encode(accounts: List<SteamAccount>, createdAt: Long = System.currentTimeMillis()): String {
        return json.encodeToString(
            SteamBackupPayload(
                createdAt = createdAt,
                accounts = accounts.map(SteamBackupAccount::fromAccount)
            )
        )
    }

    fun decode(value: String): SteamBackupPayload {
        val payload = runCatching { json.decodeFromString<SteamBackupPayload>(value) }
            .getOrElse { throw SteamBackupFormatException("备份内容无效", it) }
        if (payload.schema != 1) throw SteamBackupFormatException("不支持的备份内容版本")
        if (payload.accounts.any { it.steamId.isBlank() || it.maFileJson.isBlank() }) {
            throw SteamBackupFormatException("备份账号字段不完整")
        }
        if (payload.accounts.any { account ->
                runCatching { json.parseToJsonElement(account.maFileJson).jsonObject }.isFailure
            }
        ) {
            throw SteamBackupFormatException("备份账号的 maFile 内容无效")
        }
        if (payload.accounts.map { it.steamId }.distinct().size != payload.accounts.size) {
            throw SteamBackupFormatException("备份包含重复账号")
        }
        if (payload.accounts.count { it.selected } > 1) {
            throw SteamBackupFormatException("备份包含多个默认账号")
        }
        return payload
    }
}
