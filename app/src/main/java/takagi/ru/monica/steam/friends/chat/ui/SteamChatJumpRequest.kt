package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A pending "scroll to this message" request coming from search.
 *
 * The nonce is what makes the same hit repeatable: the list compares a request id, so handing it a
 * bare message id would be swallowed the second time the user picks that result.
 */
@Stable
internal class SteamChatJumpRequest {
    var messageId by mutableStateOf<String?>(null)
        private set
    var highlightId by mutableStateOf<String?>(null)
        private set
    var nonce by mutableIntStateOf(0)
        private set

    fun request(id: String) {
        messageId = id
        highlightId = id
        nonce++
    }

    fun clear() {
        messageId = null
        highlightId = null
    }

    fun consumeHighlight() {
        if (highlightId == messageId) highlightId = null
    }
}
