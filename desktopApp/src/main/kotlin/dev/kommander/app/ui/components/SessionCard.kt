package dev.kommander.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Commit
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kommander.presentation.dashboard.SessionSnapshot
import dev.kommander.presentation.dashboard.flowSteps
import java.time.Instant

private val CardShape = RoundedCornerShape(20.dp)
private val CardCorner = 20.dp

/**
 * Card estilo "Google Now" de uma sessão do Claude:
 * - cabeçalho com repositório/branch e ícone pulsante da categoria;
 * - o que está sendo feito agora (texto anima a cada troca);
 * - trilho do pedido atual (Plano → … → Deploy) com a etapa ativa pulsando;
 * - linha de pulso com um pico por ação nos últimos 90s;
 * - borda que "acende" a cada evento novo e pulsa quando precisa de você.
 */
@Composable
fun SessionCard(
    session: SessionSnapshot,
    now: Instant,
    featured: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = session.latest
    val visual = activity.status.category.visual
    val accent by animateColorAsState(visual.accent, tween(400), label = "accent")
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val container by animateColorAsState(
        accent.copy(alpha = if (session.needsAttention) 0.16f else 0.07f).compositeOver(surface),
        tween(400),
        label = "container",
    )

    // Brilho da borda: acende a cada evento e apaga devagar.
    val flash = remember { Animatable(0f) }
    LaunchedEffect(activity.id) {
        flash.snapTo(1f)
        flash.animateTo(0f, tween(1_200))
    }
    // Pulso contínuo da borda enquanto o Claude espera você.
    val attention = remember { Animatable(0f) }
    LaunchedEffect(session.needsAttention) {
        if (!session.needsAttention) {
            attention.animateTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            attention.animateTo(0.9f, tween(650))
            attention.animateTo(0.25f, tween(650))
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val alpha = maxOf(flash.value * 0.85f, attention.value)
                if (alpha > 0.01f) {
                    val stroke = 2.dp.toPx()
                    drawRoundRect(
                        color = accent.copy(alpha = alpha),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(CardCorner.toPx()),
                        style = Stroke(width = stroke),
                    )
                }
            },
        shape = CardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = container),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (featured) 6.dp else 2.dp),
    ) {
        // Barra indeterminada fina no topo do card enquanto há trabalho em andamento.
        AnimatedVisibility(visible = session.isWorking) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = accent,
                trackColor = accent.copy(alpha = 0.15f),
            )
        }

        Column(
            Modifier
                // Faixa lateral com a cor da categoria.
                .drawBehind { drawRect(accent, size = Size(5.dp.toPx(), size.height)) }
                .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
        ) {
            Header(session, accent, featured, onDismiss)

            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = activity,
                transitionSpec = {
                    (slideInVertically { it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutVertically { -it / 3 } + fadeOut(tween(160)))
                },
                contentKey = { it.id },
                label = "activity",
            ) { current ->
                Column {
                    Text(
                        text = current.message ?: current.status.label,
                        style = if (featured) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    current.target?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            ActivityFlow(session.flowSteps())

            Spacer(Modifier.height(6.dp))
            PulseStrip(
                recent = session.recent,
                accent = accent,
                working = session.isWorking,
                height = if (featured) 48.dp else 36.dp,
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(session.agent)
                    append(" · ")
                    append(formatRelative(activity.timestamp, now))
                    append(" · ")
                    append(session.eventCount)
                    append(if (session.eventCount == 1) " evento" else " eventos")
                    activity.tool?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Header(session: SessionSnapshot, accent: Color, featured: Boolean, onDismiss: () -> Unit) {
    val visual = session.latest.status.category.visual
    Row(verticalAlignment = Alignment.CenterVertically) {
        PulsingIcon(
            icon = visual.icon,
            contentDescription = visual.label,
            accent = accent,
            pulsing = session.isWorking || session.needsAttention,
            urgent = session.needsAttention,
            size = if (featured) 48.dp else 40.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = session.repository?.name ?: "Sem repositório",
                style = if (featured) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                session.repository?.owner?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                session.repository?.branch?.let {
                    Icon(
                        Icons.Rounded.Commit,
                        contentDescription = "branch",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        StatusPill(session.latest.status.label, accent)
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Remover sessão", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusPill(text: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = accent.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
    ) {
        AnimatedContent(text, label = "status") { label ->
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
        }
    }
}
