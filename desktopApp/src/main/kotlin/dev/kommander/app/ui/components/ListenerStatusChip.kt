package dev.kommander.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.kommander.domain.model.ListenerState

/** Status do servidor de eventos. Com [live], o ponto verde "respira" (há sessão trabalhando). */
@Composable
fun ListenerStatusChip(state: ListenerState, live: Boolean = false, modifier: Modifier = Modifier) {
    val (dot, text) = when (state) {
        is ListenerState.Listening -> Color(0xFF43A047) to ":${state.port}"
        is ListenerState.Starting -> Color(0xFFF9A825) to "iniciando…"
        is ListenerState.Failed -> MaterialTheme.colorScheme.error to "offline"
        ListenerState.Stopped -> Color(0xFF78909C) to "parado"
    }
    val pulsing = live && state is ListenerState.Listening
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(pulsing) {
        if (!pulsing) {
            pulse.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(1_300, easing = LinearEasing))
        }
    }

    Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(8.dp)) {
                val r = size.minDimension / 2
                if (pulsing) {
                    val p = pulse.value
                    drawCircle(dot.copy(alpha = (1f - p) * 0.5f), radius = r * (1f + p * 1.4f))
                }
                drawCircle(dot, radius = r)
            }
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
