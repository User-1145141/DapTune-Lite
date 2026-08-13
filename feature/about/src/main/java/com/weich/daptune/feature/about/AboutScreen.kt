package com.weich.daptune.feature.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.weich.daptune.core.designsystem.AppCard
import com.weich.daptune.core.model.AppRelease
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AboutUiState(
    val automaticUpdateChecksEnabled: Boolean = true,
    val lastUpdateCheckAtEpochMillis: Long = 0L,
    val updateCheckInProgress: Boolean = false,
    val updateCheckCompleted: Boolean = false,
    val latestRelease: AppRelease? = null,
    val updateAvailable: Boolean = false,
    val updateCheckError: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    versionName: String,
    state: AboutUiState,
    onBack: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onAutomaticUpdateChecksChanged: (Boolean) -> Unit,
    onOpenRelease: (String) -> Unit,
    onOpenProject: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Equalizer,
                            contentDescription = null,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                    Text(
                        text = "DapTune",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "版本 $versionName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionHeading("更新") }
            item {
                AppCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text("自动检查更新") },
                            supportingContent = { Text("打开应用时检查，24 小时内不重复") },
                            leadingContent = {
                                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                            },
                            trailingContent = {
                                Switch(
                                    checked = state.automaticUpdateChecksEnabled,
                                    onCheckedChange = null,
                                )
                            },
                            modifier = Modifier.toggleable(
                                value = state.automaticUpdateChecksEnabled,
                                role = Role.Switch,
                                onValueChange = onAutomaticUpdateChecksChanged,
                            ),
                            colors = transparentListItemColors(),
                        )
                        SettingsDivider()
                        ListItem(
                            headlineContent = { Text("检查更新") },
                            supportingContent = {
                                Text(
                                    text = updateStatusText(state),
                                    color = if (state.updateCheckError != null) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                            },
                            trailingContent = {
                                if (state.updateCheckInProgress) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                                }
                            },
                            modifier = Modifier.clickable(
                                enabled = !state.updateCheckInProgress,
                                onClick = onCheckForUpdates,
                            ),
                            colors = transparentListItemColors(),
                        )
                        if (state.updateAvailable && state.latestRelease != null) {
                            SettingsDivider()
                            ListItem(
                                headlineContent = {
                                    Text("查看 ${state.latestRelease.tagName}")
                                },
                                supportingContent = { Text("前往 GitHub Release 下载") },
                                leadingContent = {
                                    Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                                },
                                modifier = Modifier.clickable {
                                    onOpenRelease(state.latestRelease.releasePageUrl)
                                },
                                colors = transparentListItemColors(),
                            )
                        }
                    }
                }
            }

            item { SectionHeading("项目", modifier = Modifier.padding(top = 4.dp)) }
            item {
                AppCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                ) {
                    Column {
                        ProjectRow(
                            title = "GitHub",
                            subtitle = "silverpoetry/DapTune",
                            icon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                            onClick = onOpenProject,
                        )
                        SettingsDivider()
                        ProjectRow(
                            title = "开源许可",
                            subtitle = "Apache License 2.0",
                            icon = { Icon(Icons.Outlined.Balance, contentDescription = null) },
                            onClick = onOpenLicense,
                        )
                        SettingsDivider()
                        ProjectRow(
                            title = "隐私说明",
                            subtitle = "查看网络访问与数据处理方式",
                            icon = { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) },
                            onClick = onOpenPrivacyPolicy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = icon,
        trailingContent = {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = transparentListItemColors(),
    )
}

@Composable
private fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun transparentListItemColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
)

private fun updateStatusText(state: AboutUiState): String = when {
    state.updateCheckInProgress -> "正在连接 GitHub"
    state.updateCheckError != null -> state.updateCheckError
    state.updateAvailable && state.latestRelease != null ->
        "发现新版本 ${state.latestRelease.tagName}"
    state.updateCheckCompleted -> "已是最新版本"
    state.lastUpdateCheckAtEpochMillis > 0L ->
        "上次检查 ${formatUpdateCheckTime(state.lastUpdateCheckAtEpochMillis)}"
    else -> "从 GitHub Release 获取正式版本"
}

private fun formatUpdateCheckTime(epochMillis: Long): String = runCatching {
    UpdateCheckTimeFormatter.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
    )
}.getOrDefault("未知")

private val UpdateCheckTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
