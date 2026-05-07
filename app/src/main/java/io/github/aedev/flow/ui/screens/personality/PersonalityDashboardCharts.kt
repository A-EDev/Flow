package io.github.aedev.flow.ui.screens.personality

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recommendation.ContentVector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun TasteRadarChart(
    globalVector: ContentVector,
    currentVector: ContentVector,
    breadth: Double
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val globalValues = remember(globalVector, breadth) {
        listOf(
            globalVector.pacing,
            globalVector.complexity,
            globalVector.duration,
            globalVector.isLive,
            breadth
        ).map { it.coerceIn(0.0, 1.0) }
    }
    val currentValues = remember(currentVector) {
        listOf(
            currentVector.pacing,
            currentVector.complexity,
            currentVector.duration,
            currentVector.isLive,
            (currentVector.topics.size / 30.0).coerceIn(0.0, 1.0)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
                .padding(12.dp)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) * 0.38f
            val count = globalValues.size
            val angleStep = (2 * PI / count).toFloat()

            for (ring in 1..4) {
                val ringValues = List(count) { ring / 4.0 }
                drawRadarPolygon(
                    center = center,
                    radius = radius,
                    values = ringValues,
                    angleStep = angleStep,
                    strokeColor = outline.copy(alpha = 0.55f),
                    fillColor = Color.Transparent,
                    strokeWidth = 1f
                )
            }

            repeat(count) { index ->
                val angle = index * angleStep - (PI / 2).toFloat()
                drawLine(
                    color = outline.copy(alpha = 0.62f),
                    start = center,
                    end = Offset(
                        x = center.x + radius * cos(angle),
                        y = center.y + radius * sin(angle)
                    ),
                    strokeWidth = 1f
                )
            }

            drawRadarPolygon(
                center = center,
                radius = radius,
                values = currentValues,
                angleStep = angleStep,
                strokeColor = secondary,
                fillColor = secondary.copy(alpha = 0.12f),
                strokeWidth = 2.5f
            )
            drawRadarPolygon(
                center = center,
                radius = radius,
                values = globalValues,
                angleStep = angleStep,
                strokeColor = primary,
                fillColor = primary.copy(alpha = 0.16f),
                strokeWidth = 3f
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(label = stringResource(R.string.taste_chart_profile), color = primary)
            StatusChip(label = stringResource(R.string.taste_chart_now), color = secondary)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRadarPolygon(
    center: Offset,
    radius: Float,
    values: List<Double>,
    angleStep: Float,
    strokeColor: Color,
    fillColor: Color,
    strokeWidth: Float
) {
    val path = Path()
    values.forEachIndexed { index, value ->
        val angle = index * angleStep - (PI / 2).toFloat()
        val pointRadius = radius * value.toFloat()
        val point = Offset(
            x = center.x + pointRadius * cos(angle),
            y = center.y + pointRadius * sin(angle)
        )
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()

    if (fillColor.alpha > 0f) {
        drawPath(path = path, color = fillColor)
    }
    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

@Composable
internal fun TopicDistributionStrip(topics: List<TopicInsight>) {
    val visibleTopics = remember(topics) { topics.take(7).filter { it.score > 0.0 } }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f)
    )

    if (visibleTopics.isEmpty()) return

    val maxScore = visibleTopics.maxOfOrNull { it.score }?.takeIf { it > 0.0 } ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f))
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            visibleTopics.forEachIndexed { index, topic ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = topic.score.topicWeightLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .fillMaxHeight((topic.score / maxScore).toFloat().coerceIn(0.06f, 0.86f))
                            .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                            .background(colors[index % colors.size])
                    )
                    Text(
                        text = topic.name.readableTopic(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visibleTopics.take(5).forEachIndexed { index, topic ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors[index % colors.size])
                    )
                    Text(
                        text = topic.name.readableTopic(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = topic.score.topicWeightLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
