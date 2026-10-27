package com.diaroutine.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.diaroutine.ui.theme.DiaTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val ANIMATION_MS = 700

/**
 * A half-circle gauge with a needle, as in the screenshot's "Medication Tracked".
 * [progress] is 0..1, or null to show an empty gauge with no needle.
 */
@Composable
fun NeedleGauge(
    progress: Float?,
    description: String,
    modifier: Modifier = Modifier,
    fill: Color = DiaTheme.colors.primary,
    track: Color = DiaTheme.colors.track,
    needle: Color = DiaTheme.colors.text,
    hubInner: Color = DiaTheme.colors.raised,
) {
    val animated by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), tween(ANIMATION_MS), label = "gauge")
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.7f)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val stroke = size.width * 0.14f
        val hub = stroke * 0.42f
        val center = Offset(size.width / 2f, size.height - hub - 2.dp.toPx())
        val outer = min(size.width / 2f, center.y)
        val radius = outer - stroke / 2f
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        drawArc(track, 180f, 180f, useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(stroke))
        if (progress != null) {
            drawArc(fill, 180f, 180f * animated, useCenter = false, topLeft = arcTopLeft, size = arcSize, style = Stroke(stroke))

            val angle = PI + PI * animated
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val tipLength = radius - stroke / 2f - 4.dp.toPx()
            val tip = center + direction * tipLength
            val normal = Offset(-direction.y, direction.x) * (hub * 0.45f)
            val needlePath = Path().apply {
                moveTo(center.x + normal.x, center.y + normal.y)
                lineTo(tip.x, tip.y)
                lineTo(center.x - normal.x, center.y - normal.y)
                close()
            }
            drawPath(needlePath, needle)
            drawLine(
                color = needle,
                start = center + direction * (radius - stroke / 2f),
                end = center + direction * (radius + stroke / 2f),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
            )
        }
        drawCircle(needle, hub, center)
        drawCircle(hubInner, hub * 0.42f, center)
    }
}

/** A ring that fills clockwise from the top, with content in the middle. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    color: Color = DiaTheme.colors.primary,
    track: Color = DiaTheme.colors.raised,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(ANIMATION_MS), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = min(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = Size(diameter, diameter), style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(
                    color, -90f, 360f * animated, useCenter = false, topLeft = topLeft, size = Size(diameter, diameter),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/**
 * A 240° dial of ticks — the screenshot's "Heart Health" gauge — with the target
 * range drawn in [band] and a marker at the latest value.
 */
@Composable
fun TickDial(
    value: Float?,
    rangeMin: Float,
    rangeMax: Float,
    targetLow: Float,
    targetHigh: Float,
    description: String,
    modifier: Modifier = Modifier,
    band: Color = DiaTheme.colors.primary,
    track: Color = DiaTheme.colors.outline,
    marker: Color = DiaTheme.colors.coral,
    markerRing: Color = DiaTheme.colors.raised,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val position = value?.let { ((it - rangeMin) / (rangeMax - rangeMin)).coerceIn(0f, 1f) }
    val animated by animateFloatAsState(position ?: 0f, tween(ANIMATION_MS), label = "dial")
    // A 240° arc is 1.5 radii tall, so the box is 4:3 and its centre is the arc's visual centre.
    Box(modifier.aspectRatio(4f / 3f).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val ticks = 56
            val start = 150f
            val sweep = 240f
            val outer = min(size.width / 2f, size.height / 1.5f)
            val tickLength = outer * 0.16f
            val inner = outer - tickLength
            val c = Offset(size.width / 2f, outer)
            for (i in 0 until ticks) {
                val fraction = i / (ticks - 1f)
                val tickValue = rangeMin + (rangeMax - rangeMin) * fraction
                val angle = (start + sweep * fraction) * PI / 180.0
                val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                drawLine(
                    color = if (tickValue in targetLow..targetHigh) band else track,
                    start = c + dir * inner,
                    end = c + dir * outer,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val railRadius = inner - 12.dp.toPx()
            drawArc(
                track.copy(alpha = 0.6f), start, sweep, useCenter = false,
                topLeft = Offset(c.x - railRadius, c.y - railRadius), size = Size(railRadius * 2, railRadius * 2),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
            )
            if (position != null) {
                val angle = (start + sweep * animated) * PI / 180.0
                val at = c + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * railRadius
                drawCircle(markerRing, 9.dp.toPx(), at)
                drawCircle(marker, 6.dp.toPx(), at)
            }
        }
        content()
    }
}

data class BarDatum(
    val label: String,
    /** 0..1 of the chart's height (values above 1 are capped), or null for no data. */
    val value: Float?,
    val highlight: Boolean = false,
    val valueLabel: String? = null,
)

/**
 * Rounded bars — the highlighted one solid, the rest hatched — as in the
 * screenshot's weekly chart. An optional [goal] (0..1) draws a dashed line.
 */
@Composable
fun HatchedBarChart(
    bars: List<BarDatum>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    goal: Float? = null,
    solid: Color = DiaTheme.colors.primary,
    hatchBase: Color = DiaTheme.colors.raised,
    hatchLine: Color = DiaTheme.colors.primarySoft,
    empty: Color = DiaTheme.colors.raised.copy(alpha = 0.6f),
) {
    val colors = DiaTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.text, fontWeight = FontWeight.SemiBold)
    val goalColor = colors.textMuted
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            val barWidth = min(slot * 0.66f, 44.dp.toPx())
            val top = 20.dp.toPx()
            val usable = size.height - top
            bars.forEachIndexed { i, bar ->
                val left = i * slot + (slot - barWidth) / 2f
                val value = bar.value
                val barHeight = if (value == null || value <= 0f) barWidth * 0.7f else max(barWidth, usable * min(value, 1f))
                val rect = Rect(left, size.height - barHeight, left + barWidth, size.height)
                when {
                    value == null || value <= 0f -> drawRoundRect(empty, rect.topLeft, rect.size, CornerRadius(barWidth / 2f))
                    bar.highlight -> drawRoundRect(solid, rect.topLeft, rect.size, CornerRadius(barWidth / 2f))
                    else -> drawHatched(rect, barWidth / 2f, hatchBase, hatchLine)
                }
                if (bar.valueLabel != null && bar.highlight) {
                    val text = measurer.measure(bar.valueLabel, labelStyle)
                    drawText(text, topLeft = Offset(rect.center.x - text.size.width / 2f, rect.top - text.size.height - 4.dp.toPx()))
                }
            }
            if (goal != null) {
                val y = size.height - usable * goal.coerceIn(0f, 1f)
                drawLine(
                    goalColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                )
            }
        }
        ChartLabels(bars.map { it.label }, bars.map { it.highlight })
    }
}

/**
 * Daily values as a line over a shaded target band, gaps left where a day has no value.
 */
@Composable
fun TrendChart(
    values: List<Float?>,
    labels: List<String>,
    yMin: Float,
    yMax: Float,
    description: String,
    modifier: Modifier = Modifier,
    band: ClosedFloatingPointRange<Float>? = null,
    bandLabels: Pair<String, String>? = null,
    height: Dp = 140.dp,
    line: Color = DiaTheme.colors.primary,
    bandColor: Color = DiaTheme.colors.raised,
    dotInner: Color = DiaTheme.colors.raised,
) {
    val colors = DiaTheme.colors
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(color = colors.textMuted, fontSize = MaterialTheme.typography.labelSmall.fontSize)
    // The band's labels get a gutter of their own so they never sit under a point.
    val gutter = if (band != null && bandLabels != null) 32.dp else 0.dp
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val pad = 8.dp.toPx()
            val left = gutter.toPx()
            fun y(v: Float) = pad + (size.height - 2 * pad) * (1f - ((v - yMin) / (yMax - yMin)).coerceIn(0f, 1f))
            val slot = (size.width - left) / values.size
            fun x(i: Int) = left + slot * i + slot / 2f

            if (band != null) {
                val top = y(band.endInclusive)
                val bottom = y(band.start)
                drawRoundRect(bandColor, Offset(left, top), Size(size.width - left, bottom - top), CornerRadius(10.dp.toPx()))
                bandLabels?.let { (low, high) ->
                    val highText = measurer.measure(high, axisStyle)
                    val lowText = measurer.measure(low, axisStyle)
                    drawText(highText, topLeft = Offset(0f, top - highText.size.height / 2f))
                    drawText(lowText, topLeft = Offset(0f, bottom - lowText.size.height / 2f))
                }
            }

            var previous: Offset? = null
            values.forEachIndexed { i, v ->
                if (v == null) {
                    previous = null
                    return@forEachIndexed
                }
                val point = Offset(x(i), y(v))
                previous?.let { drawLine(line, it, point, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round) }
                previous = point
            }
            values.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val point = Offset(x(i), y(v))
                drawCircle(line, 6.dp.toPx(), point)
                drawCircle(dotInner, 3.dp.toPx(), point)
            }
        }
        ChartLabels(labels, labels.indices.map { it == labels.lastIndex }, Modifier.padding(start = gutter))
    }
}

@Composable
private fun ChartLabels(labels: List<String>, emphasized: List<Boolean>, modifier: Modifier = Modifier) {
    val colors = DiaTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        labels.forEachIndexed { i, label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (emphasized.getOrElse(i) { false }) colors.text else colors.textMuted,
                fontWeight = if (emphasized.getOrElse(i) { false }) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
        }
    }
}

private fun DrawScope.drawHatched(rect: Rect, radius: Float, base: Color, line: Color) {
    val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius))) }
    clipPath(path) {
        drawRect(base, rect.topLeft, rect.size)
        val gap = 7.dp.toPx()
        var x = rect.left - rect.height
        while (x < rect.right) {
            drawLine(line, Offset(x, rect.bottom), Offset(x + rect.height, rect.top), strokeWidth = 1.5.dp.toPx())
            x += gap
        }
    }
}
