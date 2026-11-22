package takagi.ru.monica.steam.friends.chat.info.ui

import takagi.ru.monica.steam.friends.chat.telegram.TelegramSocialTheme
import takagi.ru.monica.steam.friends.chat.telegram.TelegramSocialTopBar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationshipAction
import takagi.ru.monica.steam.friends.ui.SteamFriendDetailScreen

@Composable
internal fun SteamChatFriendDetailScreen(
    friend: SteamFriend,
    actionInProgress: Boolean,
    onBack: () -> Unit,
    onStartChat: () -> Unit,
    onChangeRelationship: (SteamFriendRelationshipAction) -> Unit,
    modifier: Modifier = Modifier
) {
    TelegramSocialTheme {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TelegramSocialTopBar(
            title = stringResource(R.string.steam_friend_details_title), onBack = onBack
        )
        Box(Modifier.weight(1f)) {
            SteamFriendDetailScreen(
                friend = friend,
                actionInProgress = actionInProgress,
                onStartChat = onStartChat,
                onChangeRelationship = onChangeRelationship
            )
        }
    }
    }
}
