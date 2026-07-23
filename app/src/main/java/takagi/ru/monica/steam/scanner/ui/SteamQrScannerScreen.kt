package takagi.ru.monica.steam.scanner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.MdbxVaultStore
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.data.SteamMdbxAccountStore
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.SteamQrChallenge
import takagi.ru.monica.steam.profile.ui.SteamAvatarImage
import takagi.ru.monica.steam.scanner.data.readLastSteamQrAccountId
import takagi.ru.monica.steam.scanner.data.readSteamStorageSource
import takagi.ru.monica.steam.scanner.data.saveLastSteamQrAccountId
import takagi.ru.monica.ui.components.MonicaModalBottomSheet
import takagi.ru.monica.ui.screens.QrScannerScreen

@Composable
fun SteamQrScannerScreen(
    initialAccountId: Long?,
    onQrCodeScanned: (String, Long?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val securityManager = remember(appContext) { SecurityManager(appContext) }
    val repository = remember(appContext) {
        SteamAccountRepository(
            SteamDatabase.getDatabase(appContext).steamAccountDao(),
            securityManager
        )
    }
    val mdbxAccountStore = remember(appContext) {
        val passwordDatabase = PasswordDatabase.getDatabase(appContext)
        SteamMdbxAccountStore(
            MdbxVaultStore(
                context = appContext,
                databaseDao = passwordDatabase.localMdbxDatabaseDao(),
                securityManager = securityManager,
                remoteSourceDao = passwordDatabase.mdbxRemoteSourceDao(),
                passwordEntryDao = passwordDatabase.passwordEntryDao(),
                secureItemDao = passwordDatabase.secureItemDao(),
                customFieldDao = passwordDatabase.customFieldDao()
            )
        )
    }
    val storageSource = remember(context) { readSteamStorageSource(context) }
    val rememberedAccountId = remember(context) { readLastSteamQrAccountId(context) }
    var accounts by remember { mutableStateOf(emptyList<SteamAccount>()) }
    var selectedAccountId by rememberSaveable(initialAccountId, rememberedAccountId) {
        mutableStateOf(initialAccountId ?: rememberedAccountId)
    }
    var showAccountPicker by remember { mutableStateOf(false) }

    LaunchedEffect(repository, mdbxAccountStore, storageSource) {
        accounts = try {
            withContext(Dispatchers.IO) {
                when (storageSource) {
                    SteamStorageSource.Local -> repository.getAccounts()
                    is SteamStorageSource.Mdbx -> runCatching {
                        mdbxAccountStore
                            .loadAccounts(storageSource.databaseId)
                            .map { it.account }
                    }.getOrDefault(emptyList())
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // The scanner remains usable even when the selected storage
            // provider is temporarily unavailable or its encrypted database
            // cannot be opened.
            SteamDiagLogger.append(
                "qr_scan account_load failed type=${error::class.java.simpleName}"
            )
            emptyList()
        }
    }

    LaunchedEffect(initialAccountId, rememberedAccountId, accounts) {
        val existingIds = accounts.map { it.id }.toSet()
        selectedAccountId = when {
            initialAccountId != null && initialAccountId in existingIds -> initialAccountId
            selectedAccountId != null && selectedAccountId in existingIds -> selectedAccountId
            rememberedAccountId != null && rememberedAccountId in existingIds -> rememberedAccountId
            else -> accounts.firstOrNull { it.selected }?.id ?: accounts.firstOrNull()?.id ?: selectedAccountId
        }
    }

    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }

    LaunchedEffect(appContext) {
        SteamDiagLogger.initialize(appContext)
        SteamDiagLogger.append(
            "qr_scan label=steam_qr event=screen_enter has_initial_account=${initialAccountId != null}"
        )
    }

    if (showAccountPicker) {
        SteamQrAccountPickerDialog(
            accounts = accounts,
            selectedAccountId = selectedAccountId,
            onSelectAccount = { account ->
                selectedAccountId = account.id
                saveLastSteamQrAccountId(context, account.id)
                showAccountPicker = false
            },
            onDismissRequest = { showAccountPicker = false }
        )
    }

    val handleValidQr: (String) -> Unit = { qrData ->
        val accountId = selectedAccount?.id ?: selectedAccountId
        saveLastSteamQrAccountId(context, accountId)
        onQrCodeScanned(qrData, accountId)
    }
    val bottomContent: @Composable (launchGallery: () -> Unit) -> Unit = { launchGallery ->
        SteamQrScannerBottomContent(
            selectedAccount = selectedAccount,
            onSelectAccount = { showAccountPicker = true },
            onPickFromGallery = launchGallery
        )
    }

    QrScannerScreen(
        onQrCodeScanned = handleValidQr,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        title = stringResource(R.string.scan_qr_code_title),
        subtitle = stringResource(R.string.qr_align_hint),
        allowedFormats = listOf(BarcodeFormat.QR_CODE),
        resultValidator = ::isValidSteamQrPayload,
        invalidResultMessage = stringResource(R.string.steam_qr_invalid_link),
        diagnosticLabel = "steam_qr",
        onDiagnostic = SteamDiagLogger::append,
        bottomContent = bottomContent
    )
}

private fun isValidSteamQrPayload(raw: String): Boolean {
    return SteamQrChallenge.parse(raw) != null
}


@Composable
private fun SteamQrScannerBottomContent(
    selectedAccount: SteamAccount?,
    onSelectAccount: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    val unavailableReason = selectedAccount?.takeUnless { it.canApproveLogins }?.steamQrUnavailableText()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = onSelectAccount,
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (selectedAccount != null) {
                SteamAvatarImage(
                    account = selectedAccount,
                    size = 36.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.steam_account_label),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = selectedAccount?.displayNameForQr()
                        ?: stringResource(R.string.steam_no_login_session),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                if (unavailableReason != null) {
                    Text(
                        text = unavailableReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        val albumInteractionSource = remember { MutableInteractionSource() }
        val albumPressed by albumInteractionSource.collectIsPressedAsState()
        val albumShape = RoundedCornerShape(18.dp)
        val albumContainerColor by animateColorAsState(
            targetValue = if (albumPressed) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            label = "SteamQrAlbumContainerColor"
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(albumShape)
                .background(albumContainerColor)
                .clickable(
                    interactionSource = albumInteractionSource,
                    indication = null,
                    onClick = onPickFromGallery
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = stringResource(R.string.steam_qr_album_select),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
            )
            Text(
                text = stringResource(R.string.steam_qr_album_select),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SteamQrAccountPickerDialog(
    accounts: List<SteamAccount>,
    selectedAccountId: Long?,
    onSelectAccount: (SteamAccount) -> Unit,
    onDismissRequest: () -> Unit
) {
    MonicaModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.steam_switch_account),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts, key = { it.id }) { account ->
                    SteamQrAccountOptionRow(
                        account = account,
                        selected = account.id == selectedAccountId,
                        onClick = { onSelectAccount(account) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamQrAccountOptionRow(
    account: SteamAccount,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            pressed -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        label = "SteamQrAccountOptionContainerColor"
    )
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val displayName = account.displayNameForQr()
    val unavailableReason = account.takeUnless { it.canApproveLogins }?.steamQrUnavailableText()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (selected) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SteamAvatarImage(
                account = account,
                size = 35.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val secondary = account.accountName.ifBlank { account.steamId }
                val secondaryText = unavailableReason ?: secondary.takeIf { it.isNotBlank() && it != displayName }
                if (!secondaryText.isNullOrBlank()) {
                    Text(
                        text = secondaryText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (unavailableReason == null) {
                            contentColor.copy(alpha = 0.68f)
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.steam_selected_account_marker),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun SteamAccount.displayNameForQr(): String {
    return displayName.ifBlank { accountName.ifBlank { steamId } }
}

@Composable
private fun SteamAccount.steamQrUnavailableText(): String {
    return when {
        !hasRealSteamId -> stringResource(R.string.steam_no_login_missing_steamid)
        sharedSecret.isBlank() -> stringResource(R.string.steam_no_login_missing_shared_secret)
        accessToken.isNullOrBlank() && refreshToken.isNullOrBlank() ->
            stringResource(R.string.steam_no_login_missing_session_detail)
        else -> stringResource(R.string.steam_no_login_session)
    }
}
