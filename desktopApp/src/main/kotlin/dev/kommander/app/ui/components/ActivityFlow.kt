package dev.kommander.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kommander.domain.model.ActivityCategory
import dev.kommander.domain.model.ToolOutcome
import dev.kommander.presentation.dashboard.FlowStage
import dev.kommander.presentation.dashboard.FlowStep
import dev.kommander.presentation.dashboard.StageState

private val NODE = 28.dp
private val BADGE = 13.dp

/** Quantas etapas cabem no trilho; as mais antigas viram um "+N" no início. */
private const val SLOTS = 7

private val FlowStage.shortLabel: String
    get() = when (this) {
        FlowStage.PLANNING -> "Plano"
        FlowStage.RESEARCH -> "Leitura"
        FlowStage.CODING -> "Código"
        FlowStage.COMMAND -> "Comando"
        FlowStage.TESTING -> "Testes"
        FlowStage.GITHUB -> "GitHub"
        FlowStage.DEPLOY -> "Deploy"
        FlowStage.WAITING -> "Você"
        FlowStage.ERROR -> "Erro"
    }

/** Etapas em que vale mostrar o ✓ de sucesso; nas demais (ler, editar) o sucesso é o normal. */
private val FlowStage.showsSuccess: Boolean
    get() = this == FlowStage.TESTING || this == FlowStage.DEPLOY

private val FlowStep.failed: Boolean get() = outcome == ToolOutcome.FAILED

private val FlowStep.accent: Color
    get() = (if (failed) ActivityCategory.ERROR else stage.category).visual.accent

/**
 * Trilho do pedido atual, na ordem real: `Plano → Leitura → Código → Testes ✗ → Código → Testes ✓`.
 * Cresce da esquerda para a direita a cada etapa nova. A etapa ativa pulsa e a conexão
 * que chega nela tem um tracejado "correndo"; falhas ficam vermelhas, com o motivo no hover.
 */
@Composable
fun ActivityFlow(steps: List<FlowStep>, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    val hidden = if (steps.size > SLOTS) steps.size - (SLOTS - 1) else 0
    val visible = steps.drop(hidden)
    // Com etapas escondidas, o primeiro slot é o marcador "+N".
    val offset = if (hidden > 0) 1 else 0
    val activeIndex = visible.indexOfFirst { it.state == StageState.ACTIVE }

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
                val slot = size.width / SLOTS
                val y = NODE.toPx() / 2
                val gap = NODE.toPx() / 2 + 4.dp.toPx()
                val lastSlot = offset + visible.lastIndex
                for (i in 0 until lastSlot) {
                    val start = Offset(slot * (i + 0.5f) + gap, y)
                    val end = Offset(slot * (i + 1.5f) - gap, y)
                    val next = visible[i + 1 - offset]
                    val color = next.accent
                    if (i + 1 - offset == activeIndex) {
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
                    } else {
                        drawLine(
                            color = if (i < offset) muted else color.copy(alpha = 0.6f),
                            start = start,
                            end = end,
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            },
    ) {
        if (hidden > 0) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(NODE).clip(CircleShape).border(1.5.dp, muted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+$hidden",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "antes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
        }
        visible.forEach { step ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                StepTooltip(step) { StageNode(step) { phase.value } }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (step.events > 1) "${step.stage.shortLabel} ${step.events}" else step.stage.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (step.state == StageState.ACTIVE || step.failed) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        step.failed || step.state == StageState.ACTIVE -> step.accent
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                )
            }
        }
        // Slots vazios à direita: o trilho "cresce" conforme o Claude avança.
        repeat(SLOTS - offset - visible.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** No hover, mostra o motivo da falha (ou quantas ações a etapa teve). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepTooltip(step: FlowStep, content: @Composable () -> Unit) {
    val text = step.detail
        ?: "${step.stage.category.visual.label} · ${step.events} ${if (step.events == 1) "ação" else "ações"}"
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp,
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).widthIn(max = 360.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        delayMillis = 300,
    ) { content() }
}

@Composable
private fun StageNode(step: FlowStep, pulse: () -> Float) {
    val accent = step.accent
    val fill by animateColorAsState(
        if (step.state == StageState.ACTIVE) accent else accent.copy(alpha = 0.2f),
        label = "fill",
    )
    val tint by animateColorAsState(if (step.state == StageState.ACTIVE) Color.White else accent, label = "tint")

    Box(Modifier.size(NODE), contentAlignment = Alignment.Center) {
        if (step.state == StageState.ACTIVE) {
            Canvas(Modifier.fillMaxSize()) {
                val p = pulse()
                drawCircle(
                    color = accent.copy(alpha = (1f - p) * 0.5f),
                    radius = size.minDimension / 2 * (1f + p * 0.8f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                step.stage.category.visual.icon,
                contentDescription = step.stage.shortLabel,
                tint = tint,
                modifier = Modifier.padding(6.dp),
            )
        }
        when {
            step.failed -> Badge(Icons.Rounded.Close, ActivityCategory.ERROR.visual.accent, "falhou")
            step.outcome == ToolOutcome.SUCCEEDED && step.stage.showsSuccess ->
                Badge(Icons.Rounded.Check, ActivityCategory.SUCCESS.visual.accent, "passou")
        }
    }
}

/** Selo no canto do nó: ✗ para falha, ✓ para testes/deploy que passaram. */
@Composable
private fun Badge(icon: ImageVector, color: Color, description: String) {
    Box(
        Modifier
            .size(NODE)
            .offset(x = NODE / 2 - BADGE / 2 + 2.dp, y = -(NODE / 2 - BADGE / 2 + 2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier
                .size(BADGE)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                .padding(1.5.dp),
        )
    }
}
