package takagi.ru.monica.steam.store.share.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import takagi.ru.monica.steam.store.domain.SteamStoreDetail

@Parcelize
data class SteamStoreGameShare(
    val appId: Int,
    val name: String,
    val storeUrl: String
) : Parcelable {
    val messageBody: String
        get() = messageBody(caption = "")

    fun messageBody(caption: String): String = buildString {
        caption.trim().takeIf(String::isNotBlank)?.let { text ->
            append(text)
            append("\n\n")
        }
        append(name.trim())
        append('\n')
        append(storeUrl.trim())
    }
}

internal fun SteamStoreDetail.toGameShare(): SteamStoreGameShare = SteamStoreGameShare(
    appId = appId,
    name = name,
    storeUrl = storeUrl
)
