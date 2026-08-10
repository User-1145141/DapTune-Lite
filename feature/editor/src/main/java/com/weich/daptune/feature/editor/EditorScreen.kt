package com.weich.daptune.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weich.daptune.core.designsystem.AppCard
import com.weich.daptune.core.designsystem.DapTuneTopAppBar
import com.weich.daptune.core.designsystem.EqBandTrackHeight
import com.weich.daptune.core.designsystem.EqCurveOverview
import com.weich.daptune.core.designsystem.GainScale
import com.weich.daptune.core.designsystem.StatusPill
import com.weich.daptune.core.designsystem.VerticalBandSlider
import com.weich.daptune.core.designsystem.formatFrequency
import com.weich.daptune.core.designsystem.formatFrequencyWithUnit
import com.weich.daptune.core.designsystem.formatGain
import com.weich.daptune.core.model.DapBandPlan
import com.weich.daptune.core.model.EqProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bandListState = rememberLazyListState()
    var showTools by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf<String?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("无法读取文件")
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "导入配置"
                    text to name
                }
            }.onSuccess { (text, name) -> viewModel.importText(text, name) }
                .onFailure { snackbar.showSnackbar(it.message ?: "无法读取文件") }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Message -> snackbar.showSnackbar(event.text)
                is EditorEvent.SuggestSaveName -> saveName = event.name
            }
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DapTuneTopAppBar(
                title = "调音",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            fileLauncher.launch(arrayOf("text/*", "application/json", "application/octet-stream"))
                        },
                    ) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = "导入配置")
                    }
                    IconButton(onClick = { showTools = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "曲线处理")
                    }
                    IconButton(onClick = viewModel::requestSave) {
                        Icon(Icons.Outlined.Save, contentDescription = "保存配置")
                    }
                },
            )
        },
        bottomBar = {
            EditorActionBar(
                routeName = state.route.displayName,
                isDirty = state.isDirty,
                isApplying = state.isApplying,
                canApply = state.canApply,
                onReset = viewModel::resetChanges,
                onApply = viewModel::apply,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ProfileSelector(
                    profiles = state.profiles,
                    selected = state.selectedProfile,
                    isDirty = state.isDirty,
                    onSelect = viewModel::selectProfile,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                CurveOverviewCard(
                    curve = state.curve,
                    selectedBand = state.selectedBand,
                    onBandSelected = { index ->
                        viewModel.selectBand(index)
                        scope.launch { bandListState.animateScrollToItem(index) }
                    },
                    capabilityLabel = capabilityLabel(state.capability),
                    capabilityReady = state.capability?.isReady == true,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                BandEditorCard(
                    curve = state.curve,
                    selectedBand = state.selectedBand,
                    listState = bandListState,
                    onBandSelected = viewModel::selectBand,
                    onGainChange = viewModel::setGain,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    if (showTools) {
        ModalBottomSheet(
            onDismissRequest = { showTools = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = "曲线处理",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            CurveToolItem("峰值归零", "整体下移，最高增益变为 0 dB") {
                viewModel.transform(CurveAction.PEAK_TO_ZERO)
                showTools = false
            }
            CurveToolItem("均值归零", "将曲线平均值移到 0 dB") {
                viewModel.transform(CurveAction.MEAN_TO_ZERO)
                showTools = false
            }
            CurveToolItem("平滑", "减小相邻频段的突变") {
                viewModel.transform(CurveAction.SMOOTH)
                showTools = false
            }
            CurveToolItem("反相", "交换提升与衰减") {
                viewModel.transform(CurveAction.INVERT)
                showTools = false
            }
            CurveToolItem("全部归零", "恢复平直曲线") {
                viewModel.transform(CurveAction.FLATTEN)
                showTools = false
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    saveName?.let { suggestion ->
        SaveProfileDialog(
            initialName = suggestion,
            canOverwrite = state.selectedProfile?.isBuiltIn == false,
            onDismiss = { saveName = null },
            onOverwrite = { name ->
                viewModel.save(name, overwrite = true)
                saveName = null
            },
            onSaveAs = { name ->
                viewModel.save(name, overwrite = false)
                saveName = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSelector(
    profiles: List<EqProfile>,
    selected: EqProfile?,
    isDirty: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "当前配置",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selected?.name ?: "选择配置",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isDirty) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "已修改",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            }
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Text(
                            profile.name,
                            fontWeight = if (profile.id == selected?.id) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    },
                    onClick = {
                        onSelect(profile.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CurveOverviewCard(
    curve: com.weich.daptune.core.model.EqCurve,
    selectedBand: Int,
    onBandSelected: (Int) -> Unit,
    capabilityLabel: String,
    capabilityReady: Boolean,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = formatFrequencyWithUnit(DapBandPlan.frequenciesHz[selectedBand]),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${formatGain(curve[selectedBand])} dB",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                StatusPill(text = capabilityLabel, positive = capabilityReady)
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(212.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(34.dp)
                        .padding(vertical = 7.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    listOf("+10", "0", "−10").forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                EqCurveOverview(
                    curve = curve,
                    selectedBand = selectedBand,
                    onBandSelected = onBandSelected,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun BandEditorCard(
    curve: com.weich.daptune.core.model.EqCurve,
    selectedBand: Int,
    listState: LazyListState,
    onBandSelected: (Int) -> Unit,
    onGainChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("20 段均衡器", style = MaterialTheme.typography.titleMedium)
                Text(
                    "0.5 dB 步进",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                GainScale(trackHeight = EqBandTrackHeight)
                Spacer(Modifier.width(6.dp))
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .height(356.dp),
                    contentPadding = PaddingValues(end = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    itemsIndexed(
                        items = DapBandPlan.frequenciesHz.toList(),
                        key = { index, _ -> index },
                    ) { index, frequency ->
                        VerticalBandSlider(
                            frequencyLabel = formatFrequency(frequency),
                            valueQ4 = curve[index],
                            onValueChange = { onGainChange(index, it) },
                            selected = selectedBand == index,
                            onSelected = { onBandSelected(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorActionBar(
    routeName: String,
    isDirty: Boolean,
    isApplying: Boolean,
    canApply: Boolean,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isDirty) {
                FilledTonalButton(
                    onClick = onReset,
                    modifier = Modifier.height(52.dp),
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("还原")
                }
            }
            Button(
                onClick = onApply,
                enabled = canApply,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                if (isApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        text = "应用到 $routeName",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurveToolItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun SaveProfileDialog(
    initialName: String,
    canOverwrite: Boolean,
    onDismiss: () -> Unit,
    onOverwrite: (String) -> Unit,
    onSaveAs: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存配置") },
        text = {
            TextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { onSaveAs(name) },
                    enabled = name.isNotBlank(),
                ) { Text("另存为") }
                if (canOverwrite) {
                    TextButton(
                        onClick = { onOverwrite(name) },
                        enabled = name.isNotBlank(),
                    ) { Text("覆盖保存") }
                }
            }
        },
    )
}

private fun capabilityLabel(capability: com.weich.daptune.core.model.DapCapability?): String = when {
    capability == null -> "检测中"
    capability.isReady -> "Dolby 已就绪"
    !capability.descriptorFound -> "不受支持"
    !capability.hasControl -> "控制权占用"
    !capability.dapEnabled -> "Dolby 已关闭"
    else -> "不可用"
}
