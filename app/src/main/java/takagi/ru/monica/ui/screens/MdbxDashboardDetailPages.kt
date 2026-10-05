package takagi.ru.monica.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.repository.MdbxHealthRepairItem
import takagi.ru.monica.repository.MdbxHealthRepairItemKind
import takagi.ru.monica.repository.MdbxHealthSeverity
import takagi.ru.monica.repository.MdbxVaultDiagnostics
import takagi.ru.monica.viewmodel.MdbxViewModel

private data class MdbxHealthCheckPresentation(
    val title: String,
    val description: String,
    val value: String,
    val icon: ImageVector,
    val hasIssue: Boolean
)

@Composable
internal fun MdbxHealthDetailPage(
    database: LocalMdbxDatabase,
    diagnostics: MdbxVaultDiagnostics?,
    onRefreshDiagnostics: () -> Unit,
    onOpenMaintenance: () -> Unit,
    onOpenSnapshots: () -> Unit,
    onOpenCommitHistory: () -> Unit,
    onOpenAttachments: () -> Unit,
    onStartAutomaticRepair: (() -> Unit)? = null,
    repairInProgress: Boolean = false
) {
    var showPassedChecks by rememberSaveable(database.id) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (diagnostics == null) {
            item {
                MdbxDetailHeroCard(
                    icon = Icons.Default.Security,
                    title = "正在检查数据库",
                    subtitle = "${database.name} 的完整性与结构状态正在读取",
                    warning = false
                )
            }
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            item {
                OutlinedButton(
                    onClick = onRefreshDiagnostics,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新检查")
                }
            }
        } else {
            val checks = diagnostics.healthCheckPresentations()
            val guidance = diagnostics.healthGuidance()
            val issueCount = diagnostics.healthIssueCount
            val noticeCount = diagnostics.healthNoticeCount
            val passedCheckCount = checks.count { !it.hasIssue }
            val visibleChecks = if (issueCount > 0 && !showPassedChecks) {
                checks.filter(MdbxHealthCheckPresentation::hasIssue)
            } else {
                checks
            }
            item {
                MdbxDetailHeroCard(
                    icon = when {
                        issueCount > 0 -> Icons.Default.Warning
                        noticeCount > 0 -> Icons.Default.Info
                        else -> Icons.Default.CheckCircle
                    },
                    title = when {
                        issueCount > 0 -> "$issueCount 个问题需要处理"
                        noticeCount > 0 -> "$noticeCount 项状态需要关注"
                        else -> "数据库健康正常"
                    },
                    subtitle = when {
                        issueCount > 0 -> "下方提供了 ${database.name} 各类异常的影响和推荐处理步骤"
                        noticeCount > 0 -> "核心数据校验通过，完成下方提示后可再次检查"
                        else -> "${database.name} 的文件、完整性和引用关系均通过检查"
                    },
                    warning = issueCount > 0
                )
            }
            item {
                if (issueCount > 0 && onStartAutomaticRepair != null) {
                    FilledTonalButton(
                        onClick = onStartAutomaticRepair,
                        enabled = !repairInProgress,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        if (repairInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(19.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (repairInProgress) "正在准备安全处理" else "一键处理可修复异常")
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefreshDiagnostics,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重新检查")
                    }
                    FilledTonalButton(
                        onClick = onOpenMaintenance,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("诊断维护")
                    }
                }
            }
            if (guidance.isNotEmpty()) {
                item {
                    MdbxDetailSectionLabel(
                        "建议处理",
                        "按异常类型列出影响和推荐步骤，底层诊断原文默认收起"
                    )
                }
                guidance.forEach { item ->
                    item(key = "health-guidance-${item.id}") {
                        MdbxHealthGuidanceCard(
                            guidance = item,
                            onRefreshDiagnostics = onRefreshDiagnostics,
                            onOpenMaintenance = onOpenMaintenance,
                            onOpenSnapshots = onOpenSnapshots,
                            onOpenCommitHistory = onOpenCommitHistory,
                            onOpenAttachments = onOpenAttachments
                        )
                    }
                }
            }
            item {
                MdbxDetailSectionLabel(
                    "基础检查",
                    if (issueCount > 0 && !showPassedChecks) {
                        "优先显示异常项目，$passedCheckCount 项正常检查已收起"
                    } else {
                        "处理完成后可在这里核对各项数据库状态"
                    }
                )
            }
            visibleChecks.forEach { check ->
                item(key = "health-check-${check.title}") {
                    MdbxHealthCheckCard(check)
                }
            }
            if (issueCount > 0 && passedCheckCount > 0) {
                item {
                    OutlinedButton(
                        onClick = { showPassedChecks = !showPassedChecks },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(
                            if (showPassedChecks) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (showPassedChecks) "收起正常检查" else "显示 $passedCheckCount 项正常检查")
                    }
                }
            }
            item {
                MdbxDetailInformationCard(
                    title = "数据库信息",
                    rows = listOf(
                        MdbxDetailInformationRow("同步状态", diagnostics.lastSyncStatus),
                        MdbxDetailInformationRow("格式版本", diagnostics.formatVersion ?: "未提供"),
                        MdbxDetailInformationRow("文件体积", formatBytes(diagnostics.fileSizeBytes)),
                        MdbxDetailInformationRow("当前客户端", diagnostics.currentDeviceId ?: "未提供"),
                        MdbxDetailInformationRow("文件位置", diagnostics.filePath ?: "未提供")
                    )
                )
            }
        }
    }
}

@Composable
internal fun MdbxAttachmentDetailPage(
    database: LocalMdbxDatabase,
    diagnostics: MdbxVaultDiagnostics?,
    onRefreshDiagnostics: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (diagnostics == null) {
            item {
                MdbxDetailHeroCard(
                    icon = Icons.Default.Storage,
                    title = "正在读取附件状态",
                    subtitle = "${database.name} 的附件索引与存储信息正在统计",
                    warning = false
                )
            }
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        } else {
            val mismatchCount = diagnostics.attachmentChunkMismatchCount
            item {
                MdbxDetailHeroCard(
                    icon = if (mismatchCount > 0) Icons.Default.Warning else Icons.Default.Storage,
                    title = when {
                        mismatchCount > 0 -> "$mismatchCount 个附件分片异常"
                        diagnostics.attachmentCount == 0 -> "当前没有附件"
                        else -> "附件存储正常"
                    },
                    subtitle = when {
                        mismatchCount > 0 -> "附件内容与分片索引存在差异，建议进入诊断维护后重新检查"
                        diagnostics.attachmentCount == 0 -> "${database.name} 尚未保存任何附件内容"
                        else -> "${database.name} 共保存 ${diagnostics.attachmentCount} 个附件"
                    },
                    warning = mismatchCount > 0
                )
            }
            item {
                OutlinedButton(
                    onClick = onRefreshDiagnostics,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新检查附件")
                }
            }
            item { MdbxDetailSectionLabel("存储概览", "区分数据库记录、外部引用和实际占用空间") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MdbxDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Storage,
                        label = "附件文件",
                        value = diagnostics.attachmentCount.toString()
                    )
                    MdbxDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Folder,
                        label = "外部引用",
                        value = diagnostics.externalAttachmentCount.toString()
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MdbxDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Info,
                        label = "原始体积",
                        value = formatBytes(diagnostics.originalAttachmentBytes)
                    )
                    MdbxDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Storage,
                        label = "实际占用",
                        value = formatBytes(diagnostics.storedAttachmentBytes)
                    )
                }
            }
            item {
                MdbxAttachmentIntegrityCard(
                    mismatchCount = mismatchCount,
                    attachmentCount = diagnostics.attachmentCount
                )
            }
            item {
                MdbxDetailInformationCard(
                    title = "存储说明",
                    rows = listOf(
                        MdbxDetailInformationRow(
                            "数据库附件",
                            "附件元数据和受保护内容由当前 MDBX 数据库管理"
                        ),
                        MdbxDetailInformationRow(
                            "外部引用",
                            if (diagnostics.externalAttachmentCount > 0) {
                                "${diagnostics.externalAttachmentCount} 个附件通过外部内容引用保存"
                            } else {
                                "没有使用外部内容引用"
                            }
                        ),
                        MdbxDetailInformationRow(
                            "分片状态",
                            if (mismatchCount > 0) "$mismatchCount 个分片需要检查" else "索引与附件内容一致"
                        )
                    )
                )
            }
        }
    }
}

private fun MdbxVaultDiagnostics.healthCheckPresentations(): List<MdbxHealthCheckPresentation> {
    val checks = listOf(
        MdbxHealthCheckPresentation(
            title = if (isReadable) "数据库文件可读取" else "数据库文件无法读取",
            description = if (isReadable) {
                "Monica 可以打开并读取当前数据库文件"
            } else {
                unavailableReason ?: "当前本地副本不可用，请检查文件位置与访问权限"
            },
            value = if (isReadable) "正常" else "需要处理",
            icon = if (isReadable) Icons.Default.CheckCircle else Icons.Default.CloudOff,
            hasIssue = !isReadable
        ),
        MdbxHealthCheckPresentation(
            title = if (integrityOk) "完整性检查通过" else "完整性检查未通过",
            description = when {
                integrityOk && healthNoticeCount > 0 -> {
                    "核心数据校验通过，另有 $healthNoticeCount 项状态提示"
                }
                integrityOk -> "数据库结构与校验信息一致"
                healthIssues.count { it.severity.requiresAction } > 0 -> {
                    "检测到 ${healthIssues.count { it.severity.requiresAction }} 项完整性异常，请按上方建议处理"
                }
                else -> "数据库返回了完整性异常，请查看上方处理建议"
            },
            value = if (integrityOk) "正常" else "需要处理",
            icon = Icons.Default.Security,
            hasIssue = !integrityOk
        ),
        MdbxHealthCheckPresentation(
            title = "提交父引用",
            description = if (danglingParentCount > 0) {
                "发现 $danglingParentCount 个提交引用了不存在的父提交，可能影响历史关系"
            } else {
                "所有提交都能找到对应的父提交"
            },
            value = if (danglingParentCount > 0) "$danglingParentCount 个异常" else "正常",
            icon = Icons.Default.History,
            hasIssue = danglingParentCount > 0
        ),
        MdbxHealthCheckPresentation(
            title = "分支头引用",
            description = if (danglingBranchHeadCount > 0) {
                "发现 $danglingBranchHeadCount 个分支指向不存在的提交"
            } else {
                "所有分支都指向有效提交"
            },
            value = if (danglingBranchHeadCount > 0) "$danglingBranchHeadCount 个异常" else "正常",
            icon = Icons.AutoMirrored.Filled.CallMerge,
            hasIssue = danglingBranchHeadCount > 0
        ),
        MdbxHealthCheckPresentation(
            title = "设备同步位置",
            description = if (danglingDeviceHeadCount > 0) {
                "发现 $danglingDeviceHeadCount 个设备的提交位置缺失、落后或归属异常"
            } else {
                "所有设备状态都指向有效提交"
            },
            value = if (danglingDeviceHeadCount > 0) "$danglingDeviceHeadCount 个异常" else "正常",
            icon = Icons.Default.Storage,
            hasIssue = danglingDeviceHeadCount > 0
        ),
        MdbxHealthCheckPresentation(
            title = "附件分片",
            description = if (attachmentChunkMismatchCount > 0) {
                "发现 $attachmentChunkMismatchCount 个附件的分片索引与内容不一致"
            } else {
                "附件分片索引与内容一致"
            },
            value = if (attachmentChunkMismatchCount > 0) "$attachmentChunkMismatchCount 个异常" else "正常",
            icon = Icons.Default.Storage,
            hasIssue = attachmentChunkMismatchCount > 0
        )
    )
    return checks.sortedByDescending(MdbxHealthCheckPresentation::hasIssue)
}

@Composable
internal fun MdbxDetailHeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    warning: Boolean
) {
    val containerColor = if (warning) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (warning) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = contentColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(26.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun MdbxDetailSectionLabel(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MdbxHealthCheckCard(check: MdbxHealthCheckPresentation) {
    val accentColor = if (check.hasIssue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val iconContainer = if (check.hasIssue) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = iconContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(check.icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(21.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        check.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        check.value,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    check.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MdbxHealthGuidanceCard(
    guidance: MdbxHealthGuidance,
    onRefreshDiagnostics: () -> Unit,
    onOpenMaintenance: () -> Unit,
    onOpenSnapshots: () -> Unit,
    onOpenCommitHistory: () -> Unit,
    onOpenAttachments: () -> Unit
) {
    var detailsExpanded by rememberSaveable(guidance.id) { androidx.compose.runtime.mutableStateOf(false) }
    val requiresAction = guidance.severity.requiresAction
    val accentColor = when (guidance.severity) {
        MdbxHealthSeverity.CRITICAL, MdbxHealthSeverity.ERROR -> MaterialTheme.colorScheme.error
        MdbxHealthSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
        MdbxHealthSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    val containerColor = when (guidance.severity) {
        MdbxHealthSeverity.CRITICAL, MdbxHealthSeverity.ERROR -> {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
        }
        MdbxHealthSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.46f)
        MdbxHealthSeverity.INFO -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val icon = when {
        guidance.category.contains("tombstone") || guidance.category == "purge-receipts" -> Icons.Default.Delete
        guidance.category == "attachment-chunks" -> Icons.Default.Storage
        guidance.category == "snapshots" -> Icons.Default.Restore
        guidance.category.startsWith("commit") || guidance.category == "stale-heads" -> Icons.Default.History
        guidance.category == "orphans" || guidance.category == "collection-profiles" -> Icons.Default.Folder
        else -> Icons.Default.Security
    }
    val actionClick = when (guidance.action) {
        MdbxHealthGuidanceAction.RECHECK -> onRefreshDiagnostics
        MdbxHealthGuidanceAction.MAINTENANCE -> onOpenMaintenance
        MdbxHealthGuidanceAction.SNAPSHOTS -> onOpenSnapshots
        MdbxHealthGuidanceAction.COMMIT_HISTORY -> onOpenCommitHistory
        MdbxHealthGuidanceAction.ATTACHMENTS -> onOpenAttachments
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            guidance.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when (guidance.severity) {
                                MdbxHealthSeverity.CRITICAL -> "严重"
                                MdbxHealthSeverity.ERROR -> "需要处理"
                                MdbxHealthSeverity.WARNING -> "需要关注"
                                MdbxHealthSeverity.INFO -> "提示"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        guidance.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        "可能影响",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        guidance.impact,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "建议处理",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                guidance.steps.forEachIndexed { index, step ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = MaterialTheme.shapes.small,
                            color = accentColor.copy(alpha = 0.14f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    (index + 1).toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            step,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = detailsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    Text(
                        "技术详情",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    guidance.technicalDetails.take(6).forEach { detail ->
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (guidance.technicalDetails.size > 6) {
                        Text(
                            "另有 ${guidance.technicalDetails.size - 6} 项同类诊断",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { detailsExpanded = !detailsExpanded },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(
                        if (detailsExpanded) "收起详情" else "技术详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                TextButton(
                    onClick = actionClick,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Icon(
                        when (guidance.action) {
                            MdbxHealthGuidanceAction.RECHECK -> Icons.Default.Sync
                            MdbxHealthGuidanceAction.MAINTENANCE -> Icons.Default.ReportProblem
                            MdbxHealthGuidanceAction.SNAPSHOTS -> Icons.Default.Restore
                            MdbxHealthGuidanceAction.COMMIT_HISTORY -> Icons.Default.History
                            MdbxHealthGuidanceAction.ATTACHMENTS -> Icons.Default.Storage
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when (guidance.action) {
                            MdbxHealthGuidanceAction.RECHECK -> "重新检查"
                            MdbxHealthGuidanceAction.MAINTENANCE -> "诊断维护"
                            MdbxHealthGuidanceAction.SNAPSHOTS -> "查看快照"
                            MdbxHealthGuidanceAction.COMMIT_HISTORY -> "查看历史"
                            MdbxHealthGuidanceAction.ATTACHMENTS -> "查看附件"
                        },
                        fontWeight = if (requiresAction) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun MdbxHealthRepairDialog(
    state: MdbxViewModel.MdbxHealthRepairState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onKeepContent: () -> Unit,
    onDeleteObject: (MdbxHealthRepairItem) -> Unit
) {
    when (state) {
        MdbxViewModel.MdbxHealthRepairState.Hidden -> Unit
        is MdbxViewModel.MdbxHealthRepairState.Planning -> {
            MdbxHealthRepairProgressDialog(
                title = "正在分析可修复项",
                message = "正在为 ${state.databaseName} 生成事务化处理计划，不会在分析阶段写入数据库。"
            )
        }
        is MdbxViewModel.MdbxHealthRepairState.Applying -> {
            MdbxHealthRepairProgressDialog(
                title = "正在安全处理",
                message = "将处理 ${state.itemCount} 项异常。MDBX3 会先创建恢复快照，再在单个事务中完成写入和复查。"
            )
        }
        is MdbxViewModel.MdbxHealthRepairState.Reviewing -> {
            val item = state.currentItem ?: return
            AlertDialog(
                onDismissRequest = onCancel,
                icon = { Icon(Icons.Default.ReportProblem, contentDescription = null) },
                title = { Text("选择冲突处理方式") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "冲突 ${state.currentIndex + 1}/${state.plan.conflictItems.size} · " +
                                "另有 ${state.plan.automaticItems.size} 项会自动安全处理",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    "${item.displayObjectType()} · ${item.objectId.take(8)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    item.conflictExplanation(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = onKeepContent,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) {
                            Text("保留当前内容并清除异常删除标记")
                        }
                        Button(
                            onClick = { onDeleteObject(item) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("删除冲突项（需要验证身份）")
                        }
                        Text(
                            "取消会终止整次处理，数据库不会产生任何写入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onCancel) { Text("取消整个处理") }
                }
            )
        }
        is MdbxViewModel.MdbxHealthRepairState.Blocked -> {
            AlertDialog(
                onDismissRequest = onCancel,
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text("无法安全自动处理") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("检测到需要人工判断或外部恢复的严重异常，Monica 没有对这些内容进行写入。")
                        state.blockers.forEach { blocker ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(blocker.category, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        blocker.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onCancel) { Text("知道了") } }
            )
        }
        is MdbxViewModel.MdbxHealthRepairState.Failed -> {
            AlertDialog(
                onDismissRequest = onCancel,
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text("处理未完成") },
                text = { Text(state.message) },
                confirmButton = { TextButton(onClick = onRetry) { Text("重新生成计划") } },
                dismissButton = { TextButton(onClick = onCancel) { Text("关闭") } }
            )
        }
    }
}

@Composable
private fun MdbxHealthRepairProgressDialog(
    title: String,
    message: String
) {
    AlertDialog(
        onDismissRequest = {},
        icon = { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {}
    )
}

private fun MdbxHealthRepairItem.displayObjectType(): String = when (objectType.lowercase()) {
    "entry" -> "密码或安全条目"
    "project" -> "分类文件夹"
    "attachment" -> "附件"
    else -> "数据库对象"
}

private fun MdbxHealthRepairItem.conflictExplanation(): String = when (kind) {
    MdbxHealthRepairItemKind.ACTIVE_OBJECT_TOMBSTONE_CONFLICT ->
        "当前内容仍然存在，但数据库同时保留了删除标记。保留会清除异常删除标记；删除会移除当前内容并留下一个规范删除记录。"
    MdbxHealthRepairItemKind.MISSING_TOMBSTONE ->
        "对象已经删除但缺少同步删除标记，该项目通常可以自动补全。"
    MdbxHealthRepairItemKind.DUPLICATE_TOMBSTONES ->
        "同一对象存在多个删除标记，该项目通常可以自动归一为一个。"
}

@Composable
private fun MdbxDetailMetricCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    Card(
        modifier = modifier.heightIn(min = 92.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MdbxAttachmentIntegrityCard(
    mismatchCount: Int,
    attachmentCount: Int
) {
    val warning = mismatchCount > 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (warning) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (warning) "附件完整性需要处理" else "附件完整性正常",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (warning) {
                        "$mismatchCount 个分片异常，受影响内容需要通过诊断工具进一步核对"
                    } else if (attachmentCount == 0) {
                        "数据库当前没有附件，无需执行分片检查"
                    } else {
                        "$attachmentCount 个附件的分片索引与内容一致"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class MdbxDetailInformationRow(
    val label: String,
    val value: String
)

@Composable
private fun MdbxDetailInformationCard(
    title: String,
    rows: List<MdbxDetailInformationRow>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(76.dp)
                    )
                    Text(
                        row.value,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
