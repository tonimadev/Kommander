package dev.kommander.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kommander.presentation.dashboard.FlowStage
import dev.kommander.presentation.dashboard.FlowStep
import dev.kommander.presentation.dashboard.StageState

private val NODE = 28.dp

private val FlowStage.shortLabel: String
    get() = when (this) {
        FlowStage.PLANNING -> "Plano"
        FlowStage.RESEARCH -> "Leitura"
        FlowStage.CODING -> "Código"
        FlowStage.TESTING -> "Testes"
        FlowStage.GITHUB -> "GitHub"
        FlowStage.DEPLOY -> "Deploy"
    }

/**
 * Trilho do pedido atual: `Plano → Leitura → Código → Testes → GitHub → Deploy`.
 * Etapas feitas ficam acesas, a ativa pulsa e a conexão que chega nela tem um
 * tracejado "correndo", dando a sensação de fluxo.
 */
@Composable
fun ActivityFlow(steps: List<FlowStep>, modifier: Modifier = Modifier) {
    val activeIndex = steps.indexOfFirst { it.state == StageState.ACTIVE }
    // Só anima enquanto há etapa ativa: com o Claude parado, o app não gasta frames.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(activeIndex >= 0) {
        if (activeIndex < 0) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            phase.snapTo(0f)
            phase.animateTo(1f, tween(1_400, easing = LinearEasing))
        }
    }
    val muted = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val slot = size.width / steps.size
                val y = NODE.toPx() / 2
                val gap = NODE.toPx() / 2 + 4.dp.toPx()
                for (i in 0 until steps.lastIndex) {
                    val start = Offset(slot * (i + 0.5f) + gap, y)
                    val end = Offset(slot * (i + 1.5f) - gap, y)
                    val next = steps[i + 1]
                    val color = next.stage.category.visual.accent
                    when {
                        i + 1 == activeIndex -> {
                            val dash = 5.dp.toPx()
                            drawLine(color.copy(alpha = 0.25f), start, end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(
                                color = color,
                                start = start,
                                end = end,
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                // Fase negativa faz os traços andarem da esquerda para a direita.
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), -(phase.value * 2f % 1f) * dash * 2),
                            )
                        }
                        next.state != StageState.PENDING ->
                            drawLine(color.copy(alpha = 0.6f), start, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        else ->
                            drawLine(muted, start, end, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            },
    ) {
        steps.forEach { step ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                StageNode(step, muted) { phase.value }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (step.events > 1) "${step.stage.shortLabel} ${step.events}" else step.stage.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (step.state == StageState.ACTIVE) FontWeight.Bold else FontWeight.Normal,
                    color = when (step.state) {
                        StageState.ACTIVE -> step.stage.category.visual.accent
                        StageState.DONE -> MaterialTheme.colorScheme.onSurface
                        StageState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StageNode(step: FlowStep, muted: Color, pulse: () -> Float) {
    val visual = step.stage.category.visual
    val fill by animateColorAsState(
        when (step.state) {
            StageState.ACTIVE -> visual.accent
            StageState.DONE -> visual.accent.copy(alpha = 0.2f)
            StageState.PENDING -> Color.Transparent
        },
        label = "fill",
    )
    val tint by animateColorAsState(
        when (step.state) {
            StageState.ACTIVE -> Color.White
            StageState.DONE -> visual.accent
            StageState.PENDING -> muted
        },
        label = "tint",
    )

    Box(Modifier.size(NODE), contentAlignment = Alignment.Center) {
        if (step.state == StageState.ACTIVE) {
            Canvas(Modifier.fillMaxSize()) {
                val p = pulse()
                drawCircle(
                    color = visual.accent.copy(alpha = (1f - p) * 0.5f),
                    radius = size.minDimension / 2 * (1f + p * 0.8f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(fill)
                .then(if (step.state == StageState.PENDING) Modifier.border(1.5.dp, muted, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(visual.icon, contentDescription = step.stage.shortLabel, tint = tint, modifier = Modifier.padding(6.dp))
        }
    }
}
