package takagi.ru.monica.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.MdbxEngineType
import takagi.ru.monica.data.MdbxTigaMode

@Composable
fun MdbxEngineTypeSection(
    selectedEngine: MdbxEngineType,
    onEngineChange: (MdbxEngineType) -> Unit,
    remote: Boolean,
    allowLegacyImport: Boolean = false,
    selectedTigaMode: MdbxTigaMode? = null,
    onTigaModeChange: ((MdbxTigaMode) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val engineLabel = if (selectedEngine == MdbxEngineType.KOTLIN_MDBX1) "MDBX1（升级）" else "MDBX 3"
    val summary = listOfNotNull(engineLabel, selectedTigaMode?.label).joinToString(" · ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            ListItem(
                headlineContent = {
                    Text("数据库选项", fontWeight = FontWeight.SemiBold)
                },
                supportingContent = { Text(summary) },
                leadingContent = {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起数据库选项" else "展开数据库选项"
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
            )
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider()
                    Text("数据库引擎", style = MaterialTheme.typography.labelLarge)
                    if (allowLegacyImport) SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val engines = listOf(MdbxEngineType.RUST_MDBX2, MdbxEngineType.KOTLIN_MDBX1)
                        engines.forEachIndexed { index, engine ->
                            SegmentedButton(
                                selected = selectedEngine == engine,
                                onClick = { onEngineChange(engine) },
                                shape = SegmentedButtonDefaults.itemShape(index, engines.size)
                            ) {
                                Text(if (engine == MdbxEngineType.KOTLIN_MDBX1) "MDBX1（升级）" else "MDBX 3")
                            }
                        }
                    } else Text("MDBX 3", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            selectedEngine == MdbxEngineType.RUST_MDBX2 && remote ->
                                "通过加密数据库同步更改，保留本地副本以供离线使用"
                            selectedEngine == MdbxEngineType.RUST_MDBX2 ->
                                "数据保存在本地加密数据库中"
                            else -> "MDBX1 已停用，仅可打开以升级；原文件保留"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (selectedTigaMode != null && onTigaModeChange != null) {
                        HorizontalDivider()
                        Text(stringResource(R.string.mdbx_tiga_section), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(
                                when (selectedTigaMode) {
                                    MdbxTigaMode.POWER -> R.string.mdbx_tiga_power_desc
                                    MdbxTigaMode.MULTI -> R.string.mdbx_tiga_multi_desc
                                    MdbxTigaMode.SKY -> R.string.mdbx_tiga_sky_desc
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            MdbxTigaMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = selectedTigaMode == mode,
                                    onClick = { onTigaModeChange(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, MdbxTigaMode.entries.size)
                                ) {
                                    Text(mode.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
