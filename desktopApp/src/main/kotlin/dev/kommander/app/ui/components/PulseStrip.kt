package dev.kommander.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kommander.domain.model.ActivityCategory
import dev.kommander.domain.model.AgentActivity
import kotlinx.coroutines.delay

private const val WINDOW_MS = 90_000f

/**
 * "Eletrocardiograma" da sessão: cada atividade vira um pico colorido pela categoria,
 * que desliza para a esquerda com o passar do tempo (janela de 90s). Enquanto há
 * trabalho em andamento, uma "cabeça" pulsa na ponta direita da linha.
 *
 * O relógio é atualizado a cada frame só enquanto a sessão trabalha e é lido apenas
 * na fase de desenho, então o custo é só redesenhar este Canvas.
 */
@Composable
fun PulseStrip(
    recent: List<AgentActivity>,
    accent: Color,
    working: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
) {
    val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(working) {
        while (true) {
            if (working) {
                withFrameMillis { clock.longValue = System.currentTimeMillis() }
            } else {
                clock.longValue = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    val beat = remember { Animatable(0f) }
    LaunchedEffect(working) {
        if (!working) {
            beat.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            beat.snapTo(0f)
            beat.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing))
        }
    }

    // Um pico novo "salta" maior e assenta: dá peso visual ao evento que acabou de chegar.
    val newest = recent.firstOrNull()?.id
    val arrival = remember { Animatable(1f) }
    LaunchedEffect(newest) {
        arrival.snapTo(1.6f)
        arrival.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }

    val baselineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)

    Canvas(modifier.fillMaxWidth().height(height)) {
        val now = clock.longValue
        val right = size.width - 8.dp.toPx()
        val base = size.height * 0.62f
        val maxAmp = size.height * 0.52f
        val spikeHalf = 7.dp.toPx()

        // Linha de base com leve fade à esquerda (o "passado").
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, baselineColor, baselineColor)),
            start = Offset(0f, base),
            end = Offset(right, base),
            strokeWidth = 1.5.dp.toPx(),
        )

        recent.forEach { activity ->
            val age = (now - activity.timestamp.toEpochMilli()).coerceAtLeast(0)
            if (age > WINDOW_MS) return@forEach
            val x = right - (age / WINDOW_MS) * right
            val fade = 1f - (age / WINDOW_MS) * 0.7f
            val grow = if (activity.id == newest) arrival.value else 1f
            val amp = maxAmp * amplitudeOf(activity.status.category) * grow
            val color = activity.status.category.visual.accent.copy(alpha = fade)

            // Complexo "QRS": pequena descida, pico alto, vale e retorno à base.
            val path = Path().apply {
                moveTo(x - spikeHalf, base)
                lineTo(x - spikeHalf * 0.55f, base + amp * 0.12f)
                lineTo(x - spikeHalf * 0.15f, base - amp)
                lineTo(x + spikeHalf * 0.35f, base + amp * 0.35f)
                lineTo(x + spikeHalf * 0.75f, base)
                lineTo(x + spikeHalf, base)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        if (working) {
            val b = beat.value
            drawCircle(accent.copy(alpha = (1f - b) * 0.45f), radius = 4.dp.toPx() + b * 9.dp.toPx(), center = Offset(right, base))
            drawCircle(accent, radius = 4.dp.toPx(), center = Offset(right, base))
        } else {
            drawCircle(baselineColor, radius = 3.dp.toPx(), center = Offset(right, base))
        }
    }
}

/** Ações "pesadas" (deploy, PR, espera por você) geram picos maiores. */
private fun amplitudeOf(category: ActivityCategory): Float = when (category) {
    ActivityCategory.DEPLOY, ActivityCategory.GITHUB, ActivityCategory.WAITING, ActivityCategory.ERROR -> 1f
    ActivityCategory.CODING, ActivityCategory.TESTING -> 0.8f
    ActivityCategory.SUCCESS -> 0.7f
    ActivityCategory.PLANNING, ActivityCategory.RESEARCH -> 0.55f
    ActivityCategory.IDLE -> 0.35f
}
