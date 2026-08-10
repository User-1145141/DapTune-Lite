package com.weich.daptune.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Matches Material 3's compact-row title easing so the replacement title preserves its motion.
private val CollapsedTitleAlphaEasing = CubicBezierEasing(.8f, 0f, .8f, .15f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DapTuneTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val collapsedTitleStyle = MaterialTheme.typography.titleLarge
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    val showCollapsedTitleSemantics by remember(scrollBehavior) {
        derivedStateOf { scrollBehavior.state.collapsedFraction >= 0.5f }
    }

    Box(modifier = modifier) {
        MediumTopAppBar(
            title = {
                // The public API uses one alignment for both rows. Keep its expanded title, then
                // replace only the compact-row copy with the centered title layered below.
                val isCollapsedTitle =
                    LocalTextStyle.current.fontSize == collapsedTitleStyle.fontSize
                Text(
                    text = title,
                    modifier = if (isCollapsedTitle) {
                        Modifier
                            .graphicsLayer { alpha = 0f }
                            .clearAndSetSemantics {}
                    } else {
                        Modifier
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )

        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .height(TopAppBarDefaults.MediumAppBarCollapsedHeight)
                .graphicsLayer {
                    alpha = CollapsedTitleAlphaEasing.transform(
                        scrollBehavior.state.collapsedFraction,
                    )
                }
                .then(
                    if (showCollapsedTitleSemantics) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics {}
                    },
                )
                .wrapContentSize(Alignment.Center),
            color = colors.titleContentColor,
            style = collapsedTitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { content() },
    )
}

@Composable
fun StatusPill(
    text: String,
    positive: Boolean,
    modifier: Modifier = Modifier,
) {
    val container = if (positive) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (positive) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
