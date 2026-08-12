package com.weich.daptune.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.weich.daptune.core.model.AutoEqForm
import com.weich.daptune.core.model.AutoEqProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutoEqSearchScreen(
    state: AutoEqSearchUiState,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onImport: (AutoEqProfile) -> Unit,
    onBack: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = isActive, onBack = onBack)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("AutoEq") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                label = { Text("耳机型号") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "清空")
                        }
                    }
                } else {
                    null
                },
            )
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }

            when {
                state.errorMessage != null -> SearchMessage(
                    icon = {
                        Icon(
                            Icons.Outlined.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    },
                    text = state.errorMessage,
                    action = { TextButton(onClick = onRetry) { Text("重试") } },
                )
                state.query.trim().length < MINIMUM_SEARCH_LENGTH -> SearchMessage(
                    icon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    },
                    text = "输入耳机型号",
                )
                !state.loading && state.results.isEmpty() -> SearchMessage(
                    text = "没有匹配的配置",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(
                        items = state.results,
                        key = AutoEqProfile::relativePath,
                    ) { profile ->
                        val importing = state.importingPath == profile.relativePath
                        val imported = state.lastImportedPath == profile.relativePath
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = profile.name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text("${profile.measurementSource} · ${profile.form.displayName()}")
                            },
                            trailingContent = {
                                when {
                                    importing -> CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    imported -> Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = "已导入",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    else -> Icon(
                                        Icons.Outlined.Download,
                                        contentDescription = "导入",
                                    )
                                }
                            },
                            modifier = Modifier.clickable(
                                enabled = state.importingPath == null && !imported,
                                onClick = { onImport(profile) },
                            ),
                        )
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                    item {
                        Text(
                            text = "AutoEq 推荐结果",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(
    text: String,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.invoke()
        }
    }
}

private fun AutoEqForm.displayName(): String = when (this) {
    AutoEqForm.IN_EAR -> "入耳式"
    AutoEqForm.OVER_EAR -> "头戴式"
    AutoEqForm.EARBUD -> "耳塞式"
    AutoEqForm.UNKNOWN -> "耳机"
}

private const val MINIMUM_SEARCH_LENGTH = 2
