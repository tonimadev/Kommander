package dev.kommander.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.kommander.domain.model.ListenerState

@Composable
fun ListenerStatusChip(state: ListenerState, modifier: Modifier = Modifier) {
    val (dot, text) = when (state) {
        is ListenerState.Listening -> Color(0xFF43A047) to ":${state.port}"
        is ListenerState.Starting -> Color(0xFFF9A825) to "iniciando…"
        is ListenerState.Failed -> MaterialTheme.colorScheme.error to "offline"
        ListenerState.Stopped -> Color(0xFF78909C) to "parado"
    }
    Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
