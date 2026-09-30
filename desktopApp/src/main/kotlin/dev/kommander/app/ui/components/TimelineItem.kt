package dev.kommander.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kommander.domain.model.ActivityCategory
import dev.kommander.domain.model.AgentActivity
import dev.kommander.domain.model.ToolOutcome
import java.time.Instant

/** Linha compacta do histórico: ícone da categoria, repositório, status e alvo. */
@Composable
fun TimelineItem(activity: AgentActivity, now: Instant, showRepository: Boolean, modifier: Modifier = Modifier) {
    val failed = activity.outcome == ToolOutcome.FAILED
    val visual = (if (failed) ActivityCategory.ERROR else activity.status.category).visual
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            visual.icon,
            contentDescription = visual.label,
            tint = visual.accent,
            modifier = Modifier.size(28.dp).clip(CircleShape).background(visual.accent.copy(alpha = 0.14f)).padding(5.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showRepository) {
                    Text(
                        text = activity.repository?.name ?: activity.agent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(" · ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = if (failed) "${activity.status.label} · falhou" else activity.status.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = visual.accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            val detail = (if (failed) activity.outcomeDetail else null) ?: activity.target ?: activity.message
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (activity.target != null) FontFamily.Monospace else FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatRelative(activity.timestamp, now),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
