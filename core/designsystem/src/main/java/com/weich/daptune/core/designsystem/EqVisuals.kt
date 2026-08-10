package com.weich.daptune.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weich.daptune.core.model.DapBandPlan
import com.weich.daptune.core.model.EqCurve
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

val EqBandTrackHeight = 300.dp

@Composable
fun EqCurveOverview(
    curve: EqCurve,
    selectedBand: Int,
    onBandSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val zero = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val currentOnBandSelected by rememberUpdatedState(onBandSelected)

    Canvas(
        modifier = modifier
            .pointerInput(curve) {
                detectTapGestures { tap ->
                    val horizontalInset = with(density) { 4.dp.toPx() }
                    val verticalInset = with(density) { 8.dp.toPx() }
                    val hitRadius = with(density) { 24.dp.toPx() }
                    nearestCurveBandAt(
                        curve = curve,
                        tapX = tap.x,
                        tapY = tap.y,
                        widthPx = size.width.toFloat(),
                        heightPx = size.height.toFloat(),
                        horizontalInsetPx = horizontalInset,
                        verticalInsetPx = verticalInset,
                        hitRadiusPx = hitRadius,
                    )?.let { nearest ->
                        currentOnBandSelected(nearest)
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                }
            }
            .semantics {
                contentDescription = "20 段均衡器曲线，点按频点以选择"
            },
    ) {
        val horizontalInset = 4.dp.toPx()
        val verticalInset = 8.dp.toPx()
        val plotWidth = (size.width - horizontalInset * 2f).coerceAtLeast(1f)
        val plotHeight = (size.height - verticalInset * 2f).coerceAtLeast(1f)
        val zeroY = verticalInset + plotHeight / 2f

        fun yFor(gainQ4: Int): Float =
            verticalInset + plotHeight * (0.5f - gainQ4.toFloat() / (EqCurve.MAX_GAIN_Q4 * 2f))

        listOf(10, 5, 0, -5, -10).forEach { db ->
            val y = yFor(db * EqCurve.Q4_PER_DB)
            drawLine(
                color = if (db == 0) zero else grid,
                start = Offset(horizontalInset, y),
                end = Offset(size.width - horizontalInset, y),
                strokeWidth = if (db == 0) 1.25.dp.toPx() else 0.75.dp.toPx(),
            )
        }
        listOf(0, 4, 9, 14, 19).forEach { index ->
            val x = horizontalInset + plotWidth * index / (DapBandPlan.bandCount - 1)
            drawLine(
                color = grid.copy(alpha = 0.62f),
                start = Offset(x, verticalInset),
                end = Offset(x, size.height - verticalInset),
                strokeWidth = 0.75.dp.toPx(),
            )
        }

        val points = List(DapBandPlan.bandCount) { index ->
            curvePointForBand(
                curve = curve,
                index = index,
                widthPx = size.width,
                heightPx = size.height,
                horizontalInsetPx = horizontalInset,
                verticalInsetPx = verticalInset,
            )
        }
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(points.last().x, zeroY)
            lineTo(points.first().x, zeroY)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primary.copy(alpha = 0.24f), primary.copy(alpha = 0.025f)),
                startY = verticalInset,
                endY = size.height - verticalInset,
            ),
        )
        drawPath(
            path = linePath,
            color = primary,
            style = Stroke(width = 2.75.dp.toPx(), cap = StrokeCap.Round),
        )
        points.forEach { point ->
            drawCircle(primary, radius = 2.75.dp.toPx(), center = point)
        }
        points.getOrNull(selectedBand)?.let { point ->
            drawLine(
                color = primary.copy(alpha = 0.18f),
                start = Offset(point.x, verticalInset),
                end = Offset(point.x, size.height - verticalInset),
                strokeWidth = 1.dp.toPx(),
            )
            drawCircle(primary.copy(alpha = 0.16f), radius = 11.dp.toPx(), center = point)
            drawCircle(surface, radius = 6.5.dp.toPx(), center = point)
            drawCircle(primary, radius = 4.dp.toPx(), center = point)
        }
    }
}

@Composable
fun CurveSparkline(
    curve: EqCurve,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val zero = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = modifier.semantics { contentDescription = "均衡器曲线" },
    ) {
        val verticalInset = 3.dp.toPx()
        val plotHeight = (size.height - verticalInset * 2f).coerceAtLeast(1f)
        fun yFor(gainQ4: Int): Float =
            verticalInset + plotHeight * (0.5f - gainQ4.toFloat() / (EqCurve.MAX_GAIN_Q4 * 2f))

        listOf(10, 5, 0, -5, -10).forEach { db ->
            val y = yFor(db * EqCurve.Q4_PER_DB)
            drawLine(
                color = if (db == 0) zero else grid.copy(alpha = 0.72f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = if (db == 0) 1.dp.toPx() else 0.65.dp.toPx(),
            )
        }
        val points = List(DapBandPlan.bandCount) { index ->
            Offset(
                x = size.width * index / (DapBandPlan.bandCount - 1),
                y = yFor(curve[index]),
            )
        }
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val zeroY = yFor(0)
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(points.last().x, zeroY)
            lineTo(points.first().x, zeroY)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primary.copy(alpha = 0.2f), primary.copy(alpha = 0.015f)),
            ),
        )
        drawPath(
            path = linePath,
            color = primary,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
fun GainScale(
    modifier: Modifier = Modifier,
    trackHeight: Dp = EqBandTrackHeight,
) {
    Column(
        modifier = modifier.width(34.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .height(trackHeight)
                .padding(vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                listOf("+10", "+5", "0", "−5", "−10").forEach { label ->
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun VerticalBandSlider(
    frequencyLabel: String,
    valueQ4: Int,
    onValueChange: (Int) -> Unit,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val inactive = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.outline
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val verticalInsetPx = with(density) { 12.dp.toPx() }
    val trackHeightPx = with(density) { EqBandTrackHeight.toPx() }
    val thumbTouchSize = 48.dp
    val thumbTouchSizePx = with(density) { thumbTouchSize.toPx() }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnSelected by rememberUpdatedState(onSelected)
    var lastReportedQ4 by remember { mutableIntStateOf(valueQ4) }
    var dragging by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(trackHeightPx / 2f) }

    SideEffect {
        if (!dragging) lastReportedQ4 = valueQ4
    }

    fun yFor(gainQ4: Int): Float {
        return trackYForGainQ4(gainQ4, trackHeightPx, verticalInsetPx)
    }

    fun update(y: Float) {
        val snapped = gainQ4ForTrackPosition(y, trackHeightPx, verticalInsetPx)
        if (snapped != lastReportedQ4) {
            lastReportedQ4 = snapped
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            currentOnValueChange(snapped)
        }
    }

    val dragState = rememberDraggableState { delta ->
        dragY = (dragY + delta).coerceIn(verticalInsetPx, trackHeightPx - verticalInsetPx)
        update(dragY)
    }

    Column(
        modifier = modifier
            .width(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f) else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            )
            .clickable { currentOnSelected() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatGain(valueQ4),
            modifier = Modifier.height(24.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else {
                MaterialTheme.colorScheme.onSurface
            },
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(EqBandTrackHeight)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val top = verticalInsetPx
                val bottom = size.height - verticalInsetPx
                val usable = bottom - top
                val zeroY = (top + bottom) / 2f
                val thumbY = trackYForGainQ4(valueQ4, size.height, verticalInsetPx)

                repeat(41) { index ->
                    val y = top + usable * index / 40f
                    val length = when {
                        index % 10 == 0 -> 22.dp.toPx()
                        index % 2 == 0 -> 14.dp.toPx()
                        else -> 8.dp.toPx()
                    }
                    drawLine(
                        color = tick.copy(alpha = if (index % 10 == 0) 0.72f else 0.42f),
                        start = Offset(centerX - length / 2f, y),
                        end = Offset(centerX + length / 2f, y),
                        strokeWidth = if (index % 10 == 0) 1.15.dp.toPx() else 0.75.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                drawRoundRect(
                    color = inactive,
                    topLeft = Offset(centerX - 2.5.dp.toPx(), top),
                    size = Size(5.dp.toPx(), usable),
                    cornerRadius = CornerRadius(2.5.dp.toPx()),
                )
                drawRoundRect(
                    color = primary,
                    topLeft = Offset(centerX - 2.5.dp.toPx(), minOf(zeroY, thumbY)),
                    size = Size(5.dp.toPx(), abs(zeroY - thumbY)),
                    cornerRadius = CornerRadius(2.5.dp.toPx()),
                )
                if (selected) {
                    drawCircle(primary.copy(alpha = 0.18f), 14.dp.toPx(), Offset(centerX, thumbY))
                }
                drawCircle(primary, 9.dp.toPx(), Offset(centerX, thumbY))
                drawCircle(onPrimary, 3.25.dp.toPx(), Offset(centerX, thumbY))
            }
            val thumbY = if (dragging) dragY else yFor(valueQ4)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (thumbY - thumbTouchSizePx / 2f)
                                .coerceIn(0f, trackHeightPx - thumbTouchSizePx)
                                .roundToInt(),
                        )
                    }
                    .size(thumbTouchSize)
                    .semantics {
                        contentDescription = "$frequencyLabel 均衡器滑块"
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = valueQ4.toFloat() / EqCurve.Q4_PER_DB,
                            range = -10f..10f,
                            steps = 39,
                        )
                        setProgress { targetDb ->
                            val targetQ4 = snapGainQ4(
                                (targetDb * EqCurve.Q4_PER_DB).roundToInt(),
                            )
                            currentOnSelected()
                            currentOnValueChange(targetQ4)
                            true
                        }
                    }
                    .clickable { currentOnSelected() }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = {
                            dragging = true
                            dragY = yFor(valueQ4)
                            currentOnSelected()
                            haptic.performHapticFeedback(
                                HapticFeedbackType.GestureThresholdActivate,
                            )
                        },
                        onDragStopped = {
                            dragging = false
                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        },
                    ),
            )
        }
        Text(
            text = frequencyLabel,
            modifier = Modifier.height(20.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

fun formatFrequency(frequencyHz: Int): String = when {
    frequencyHz >= 1_000 -> {
        val value = "%.1f".format(frequencyHz / 1_000.0).trimEnd('0').trimEnd('.')
        "${value}k"
    }
    else -> frequencyHz.toString()
}

fun formatFrequencyWithUnit(frequencyHz: Int): String = when {
    frequencyHz >= 1_000 -> {
        val decimals = if (frequencyHz >= 10_000) 1 else 2
        val pattern = if (decimals == 1) "%.1f" else "%.2f"
        val value = pattern.format(frequencyHz / 1_000.0)
            .trimEnd('0')
            .trimEnd('.')
        "$value kHz"
    }
    else -> "$frequencyHz Hz"
}

fun formatGain(valueQ4: Int): String {
    val db = valueQ4.toDouble() / EqCurve.Q4_PER_DB
    return when {
        valueQ4 == 0 -> "0"
        valueQ4 % EqCurve.Q4_PER_DB == 0 -> "%+.0f".format(db)
        else -> "%+.1f".format(db)
    }
}

private fun snapGainQ4(valueQ4: Int): Int =
    ((valueQ4.toFloat() / GainStepQ4).roundToInt() * GainStepQ4)
        .coerceIn(-EqCurve.MAX_GAIN_Q4, EqCurve.MAX_GAIN_Q4)

internal fun gainQ4ForTrackPosition(
    yPx: Float,
    trackHeightPx: Float,
    verticalInsetPx: Float,
): Int {
    val usable = (trackHeightPx - verticalInsetPx * 2f).coerceAtLeast(1f)
    val fraction = ((yPx - verticalInsetPx) / usable).coerceIn(0f, 1f)
    val raw = ((0.5f - fraction) * EqCurve.MAX_GAIN_Q4 * 2f).roundToInt()
    return snapGainQ4(raw)
}

internal fun trackYForGainQ4(
    gainQ4: Int,
    trackHeightPx: Float,
    verticalInsetPx: Float,
): Float {
    val usable = (trackHeightPx - verticalInsetPx * 2f).coerceAtLeast(1f)
    val normalizedGain = gainQ4
        .coerceIn(-EqCurve.MAX_GAIN_Q4, EqCurve.MAX_GAIN_Q4)
        .toFloat() / (EqCurve.MAX_GAIN_Q4 * 2f)
    return trackHeightPx / 2f - normalizedGain * usable
}

internal fun nearestCurveBandAt(
    curve: EqCurve,
    tapX: Float,
    tapY: Float,
    widthPx: Float,
    heightPx: Float,
    horizontalInsetPx: Float,
    verticalInsetPx: Float,
    hitRadiusPx: Float,
): Int? {
    val nearest = (0 until DapBandPlan.bandCount).minByOrNull { index ->
        val point = curvePointForBand(
            curve = curve,
            index = index,
            widthPx = widthPx,
            heightPx = heightPx,
            horizontalInsetPx = horizontalInsetPx,
            verticalInsetPx = verticalInsetPx,
        )
        hypot(tapX - point.x, tapY - point.y)
    } ?: return null
    val point = curvePointForBand(
        curve = curve,
        index = nearest,
        widthPx = widthPx,
        heightPx = heightPx,
        horizontalInsetPx = horizontalInsetPx,
        verticalInsetPx = verticalInsetPx,
    )
    return nearest.takeIf { hypot(tapX - point.x, tapY - point.y) <= hitRadiusPx }
}

private fun curvePointForBand(
    curve: EqCurve,
    index: Int,
    widthPx: Float,
    heightPx: Float,
    horizontalInsetPx: Float,
    verticalInsetPx: Float,
): Offset {
    val plotWidth = (widthPx - horizontalInsetPx * 2f).coerceAtLeast(1f)
    val plotHeight = (heightPx - verticalInsetPx * 2f).coerceAtLeast(1f)
    return Offset(
        x = horizontalInsetPx + plotWidth * index / (DapBandPlan.bandCount - 1),
        y = verticalInsetPx +
            plotHeight * (0.5f - curve[index].toFloat() / (EqCurve.MAX_GAIN_Q4 * 2f)),
    )
}

private const val GainStepQ4 = EqCurve.Q4_PER_DB / 2
