package dev.kommander.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ícone da categoria com anéis de "radar" saindo dele enquanto [pulsing] for true.
 * [urgent] acelera o pulso (usado quando o Claude está esperando você).
 *
 * Os anéis são desenhados fora dos limites do ícone (Canvas não recorta) e o valor
 * animado só é lido na fase de desenho: não há recomposição a cada frame.
 */
@Composable
fun PulsingIcon(
    icon: ImageVector,
    contentDescription: String,
    accent: Color,
    pulsing: Boolean,
    urgent: Boolean = false,
    size: Dp = 44.dp,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pulsing, urgent) {
        if (!pulsing) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        val period = if (urgent) 900 else 1_600
        while (true) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(period, easing = LinearEasing))
        }
    }

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (!pulsing) return@Canvas
            val base = this.size.minDimension / 2
            // Dois anéis defasados em meio período formam o efeito de onda contínua.
            for (offset in floatArrayOf(0f, 0.5f)) {
                val p = (progress.value + offset) % 1f
                drawCircle(
                    color = accent.copy(alpha = (1f - p) * 0.55f),
                    radius = base * (1f + p * 0.9f),
                    style = Stroke(width = 2.dp.toPx() * (1f - p) + 0.5f),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(icon, label = "icon") { current ->
                Icon(current, contentDescription = contentDescription, tint = accent)
            }
        }
    }
}
