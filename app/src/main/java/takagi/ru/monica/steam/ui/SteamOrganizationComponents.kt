package takagi.ru.monica.steam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountTags
import takagi.ru.monica.steam.organization.SteamAccountAccentPalette
import takagi.ru.monica.steam.organization.SteamAccountOrganizationFilter
import takagi.ru.monica.steam.organization.SteamAccountOrganizationRules
import takagi.ru.monica.steam.organization.SteamAccountOrganizer

@Composable
internal fun SteamOrganizationFilterBar(
    accounts: List<SteamAccount>,
    filter: SteamAccountOrganizationFilter,
    onFilterChange: (SteamAccountOrganizationFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember(accounts) { SteamAccountOrganizer.groups(accounts) }
    val tags = remember(accounts) { SteamAccountOrganizer.tags(accounts) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.steam_organization_filters),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onFilterChange(SteamAccountOrganizationFilter()) },
                enabled = filter.isActive,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Close, stringResource(R.string.steam_organization_clear_filters))
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item("pinned") {
                FilterChip(
                    selected = filter.pinnedOnly,
                    onClick = { onFilterChange(filter.copy(pinnedOnly = !filter.pinnedOnly)) },
                    label = { Text(stringResource(R.string.steam_organization_pinned)) },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
            items(groups, key = { "group-$it" }) { group ->
                FilterChip(
                    selected = filter.groupName.equals(group, ignoreCase = true),
                    onClick = {
                        onFilterChange(
                            filter.copy(groupName = group.takeUnless { filter.groupName.equals(it, true) })
                        )
                    },
                    label = { Text(group, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
        if (tags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tags, key = { "tag-$it" }) { tag ->
                    FilterChip(
                        selected = filter.tag.equals(tag, ignoreCase = true),
                        onClick = {
                            onFilterChange(filter.copy(tag = tag.takeUnless { filter.tag.equals(it, true) }))
                        },
                        label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.LocalOffer, contentDescription = null) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SteamOrganizationSummary(
    account: SteamAccount,
    modifier: Modifier = Modifier
) {
    if (!account.pinned && account.groupName.isNullOrBlank() && account.tags.isEmpty() && account.note.isBlank()) return
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            account.accentArgb?.let { accent ->
                Spacer(
                    Modifier.size(12.dp).clip(CircleShape).background(Color(accent.toULong()))
                )
                Spacer(Modifier.width(8.dp))
            }
            if (account.pinned) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = stringResource(R.string.steam_organization_pinned),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = account.groupName ?: stringResource(R.string.steam_organization_ungrouped),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (account.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                account.tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        if (account.note.isNotBlank()) {
            Text(
                text = account.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SteamOrganizationEditorDialog(
    account: SteamAccount,
    onDismissRequest: () -> Unit,
    onSave: (groupName: String, tags: List<String>, accentArgb: Long?, note: String, pinned: Boolean) -> Unit
) {
    var groupName by remember(account.id, account.updatedAt) { mutableStateOf(account.groupName.orEmpty()) }
    var rawTags by remember(account.id, account.updatedAt) { mutableStateOf(account.tags.joinToString(", ")) }
    var accentArgb by remember(account.id, account.updatedAt) { mutableStateOf(account.accentArgb) }
    var note by remember(account.id, account.updatedAt) { mutableStateOf(account.note) }
    var pinned by remember(account.id, account.updatedAt) { mutableStateOf(account.pinned) }
    val validation = SteamAccountOrganizationRules.validate(groupName, rawTags, note)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.steam_organization_edit_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    account.displayName.ifBlank { account.accountName }.ifBlank { account.visibleSteamId },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text(stringResource(R.string.steam_organization_group)) },
                    singleLine = true,
                    isError = validation.groupTooLong,
                    supportingText = {
                        Text(stringResource(R.string.steam_organization_group_count, groupName.length, SteamAccountOrganizationRules.MAX_GROUP_LENGTH))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rawTags,
                    onValueChange = { rawTags = it },
                    label = { Text(stringResource(R.string.steam_organization_tags)) },
                    isError = validation.tooManyTags || validation.tagTooLong,
                    supportingText = {
                        Text(
                            when {
                                validation.tooManyTags -> stringResource(R.string.steam_organization_too_many_tags, SteamAccountTags.MAX_TAGS)
                                validation.tagTooLong -> stringResource(R.string.steam_organization_tag_too_long, SteamAccountTags.MAX_TAG_LENGTH)
                                else -> stringResource(R.string.steam_organization_tags_hint)
                            }
                        )
                    },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.steam_organization_color), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentButton(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            selected = accentArgb == null,
                            contentDescription = stringResource(R.string.steam_organization_color_none),
                            onClick = { accentArgb = null },
                            clear = true
                        )
                        SteamAccountAccentPalette.options.forEach { option ->
                            AccentButton(
                                color = Color(option.argb.toULong()),
                                selected = accentArgb == option.argb,
                                contentDescription = stringResource(R.string.steam_organization_color_choice, option.key),
                                onClick = { accentArgb = option.argb }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.steam_organization_note)) },
                    minLines = 3,
                    maxLines = 6,
                    isError = validation.noteTooLong,
                    supportingText = {
                        Text(stringResource(R.string.steam_organization_note_count, note.length, SteamAccountOrganizationRules.MAX_NOTE_LENGTH))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { pinned = !pinned }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PushPin, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.steam_organization_pinned))
                        Text(
                            stringResource(R.string.steam_organization_pinned_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = pinned, onCheckedChange = { pinned = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        groupName,
                        SteamAccountOrganizationRules.parseTags(rawTags),
                        accentArgb,
                        note,
                        pinned
                    )
                },
                enabled = validation.isValid
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun AccentButton(
    color: Color,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    clear: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.size(48.dp)
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (clear) Icons.Default.Close else Icons.Default.Check,
                contentDescription = contentDescription,
                tint = if (clear || selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
