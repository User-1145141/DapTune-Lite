package com.weich.daptune.feature.automation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BluetoothAudio
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SettingsInputHdmi
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weich.daptune.core.designsystem.AppCard
import com.weich.daptune.core.designsystem.DapTuneTopAppBar
import com.weich.daptune.core.model.EqProfile
import com.weich.daptune.core.model.KnownOutputDevice
import com.weich.daptune.core.model.OutputRouteType

private sealed interface ProfilePickerTarget {
    data object Default : ProfilePickerTarget
    data class Device(val device: KnownOutputDevice) : ProfilePickerTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    modifier: Modifier = Modifier,
    viewModel: AutomationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    var pickerTarget by remember { mutableStateOf<ProfilePickerTarget?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onNotificationPermissionResult(granted) }

    fun setAutomation(enabled: Boolean) {
        val needsPermission = enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setEnabled(enabled)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect(snackbar::showSnackbar)
    }

    val visibleDevices = remember(state.devices, state.currentRoute) {
        val current = state.devices.firstOrNull { it.route.key == state.currentRoute.key }
            ?: KnownOutputDevice(state.currentRoute, lastSeenAtEpochMillis = 0L)
        listOf(current) + state.devices.filterNot { it.route.key == current.route.key }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DapTuneTopAppBar(
                title = "自动切换",
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeading("常规") }
            item {
                AppCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text("自动切换") },
                            supportingContent = { Text("播放设备变化时应用对应配置") },
                            leadingContent = {
                                Icon(Icons.Outlined.Tune, contentDescription = null)
                            },
                            trailingContent = {
                                Switch(
                                    checked = state.settings.automationEnabled,
                                    onCheckedChange = null,
                                )
                            },
                            modifier = Modifier.toggleable(
                                value = state.settings.automationEnabled,
                                role = Role.Switch,
                                onValueChange = ::setAutomation,
                            ),
                            colors = transparentListItemColors(),
                        )
                        SettingsDivider()
                        ListItem(
                            headlineContent = { Text("开机后自动启动") },
                            supportingContent = { Text("设备重启后继续运行自动切换") },
                            leadingContent = {
                                Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null)
                            },
                            trailingContent = {
                                Switch(
                                    checked = state.settings.applyAtBoot,
                                    onCheckedChange = null,
                                )
                            },
                            modifier = Modifier.toggleable(
                                value = state.settings.applyAtBoot,
                                role = Role.Switch,
                                onValueChange = viewModel::setApplyAtBoot,
                            ),
                            colors = transparentListItemColors(),
                        )
                    }
                }
            }

            item { SectionHeading("规则", modifier = Modifier.padding(top = 4.dp)) }
            item {
                AppCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                ) {
                    ListItem(
                        headlineContent = { Text("默认配置") },
                        supportingContent = { Text("没有专属规则时使用") },
                        leadingContent = {
                            Icon(Icons.Outlined.Tune, contentDescription = null)
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = state.defaultProfile?.name ?: "平直",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable {
                            pickerTarget = ProfilePickerTarget.Default
                        },
                        colors = transparentListItemColors(),
                    )
                }
            }

            item { SectionHeading("播放设备", modifier = Modifier.padding(top = 4.dp)) }
            item {
                AppCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                ) {
                    Column {
                        visibleDevices.forEachIndexed { index, device ->
                            val assigned = state.profileFor(device.route.key)
                            DeviceRuleRow(
                                device = device,
                                assignedProfile = assigned,
                                defaultProfile = state.defaultProfile,
                                isCurrent = device.route.key == state.currentRoute.key,
                                onClick = {
                                    pickerTarget = ProfilePickerTarget.Device(device)
                                },
                            )
                            if (index != visibleDevices.lastIndex) SettingsDivider()
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "系统音效若覆盖曲线，下一次播放设备事件会重新应用规则。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    pickerTarget?.let { target ->
        val selectedId = when (target) {
            ProfilePickerTarget.Default -> state.settings.defaultProfileId
            is ProfilePickerTarget.Device -> state.profileFor(target.device.route.key)?.id
        }
        ProfileSelectionDialog(
            title = when (target) {
                ProfilePickerTarget.Default -> "默认配置"
                is ProfilePickerTarget.Device -> target.device.route.displayName
            },
            profiles = state.profiles,
            selectedId = selectedId,
            allowFollowDefault = target is ProfilePickerTarget.Device,
            defaultProfileName = state.defaultProfile?.name ?: "平直",
            onDismiss = { pickerTarget = null },
            onSelect = { profileId ->
                when (target) {
                    ProfilePickerTarget.Default -> profileId?.let(viewModel::setDefaultProfile)
                    is ProfilePickerTarget.Device -> viewModel.bind(target.device.route.key, profileId)
                }
                pickerTarget = null
            },
        )
    }
}

@Composable
private fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun DeviceRuleRow(
    device: KnownOutputDevice,
    assignedProfile: EqProfile?,
    defaultProfile: EqProfile?,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val ruleLabel = assignedProfile?.name ?: "跟随默认 · ${defaultProfile?.name ?: "平直"}"
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.route.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isCurrent) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "当前",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        supportingContent = {
            Text(
                text = ruleLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Icon(
                routeIcon(device.route.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = transparentListItemColors(),
    )
}

@Composable
private fun ProfileSelectionDialog(
    title: String,
    profiles: List<EqProfile>,
    selectedId: String?,
    allowFollowDefault: Boolean,
    defaultProfileName: String,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 344.dp),
            ) {
                items(
                    items = profiles,
                    key = EqProfile::id,
                ) { profile ->
                    ProfileChoiceRow(
                        name = profile.name,
                        selected = selectedId == profile.id,
                        onClick = { onSelect(profile.id) },
                    )
                }
                if (allowFollowDefault) {
                    item { HorizontalDivider() }
                    item {
                        ProfileChoiceRow(
                            name = "跟随默认 · $defaultProfileName",
                            selected = selectedId == null,
                            onClick = { onSelect(null) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun ProfileChoiceRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun transparentListItemColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

private fun routeIcon(type: OutputRouteType): ImageVector = when (type) {
    OutputRouteType.BUILT_IN_SPEAKER -> Icons.Outlined.Speaker
    OutputRouteType.WIRED_HEADSET -> Icons.Outlined.Headphones
    OutputRouteType.BLUETOOTH,
    OutputRouteType.BLE_AUDIO,
    -> Icons.Outlined.BluetoothAudio
    OutputRouteType.USB -> Icons.Outlined.Usb
    OutputRouteType.HDMI -> Icons.Outlined.SettingsInputHdmi
    OutputRouteType.REMOTE -> Icons.Outlined.Smartphone
    OutputRouteType.UNKNOWN -> Icons.Outlined.Speaker
}
