package dev.kommander.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kommander.presentation.dashboard.DashboardIntent
import dev.kommander.presentation.dashboard.DashboardState
import dev.kommander.presentation.dashboard.DashboardViewModel
import dev.kommander.app.ui.components.EmptyState
import dev.kommander.app.ui.components.ListenerStatusChip
import dev.kommander.app.ui.components.SessionCard
import dev.kommander.app.ui.components.TimelineItem
import dev.kommander.app.ui.components.rememberNow
import dev.kommander.domain.model.ListenerState

/** Ponto de entrada "stateful": conecta o ViewModel à UI stateless. */
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, alwaysOnTop: Boolean, onToggleAlwaysOnTop: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.onIntent(DashboardIntent.StartListening) }
    DashboardContent(state, viewModel::onIntent, alwaysOnTop, onToggleAlwaysOnTop)
}

/** UI pura: renderiza [DashboardState] e emite [DashboardIntent]s. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardState,
    onIntent: (DashboardIntent) -> Unit,
    alwaysOnTop: Boolean,
    onToggleAlwaysOnTop: () -> Unit,
) {
    val now by rememberNow()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text("Kommander", fontWeight = FontWeight.Bold)
                        Text(
                            text = subtitle(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    ListenerStatusChip(state.listener)
                    IconButton(onClick = onToggleAlwaysOnTop) {
                        Icon(
                            if (alwaysOnTop) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Manter janela no topo",
                        )
                    }
                    IconButton(onClick = { onIntent(DashboardIntent.ClearHistory) }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Limpar histórico")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            (state.listener as? ListenerState.Failed)?.let { failed ->
                item(key = "listener-error") {
                    ListenerErrorCard(failed.reason) { onIntent(DashboardIntent.RetryListening) }
                }
            }

            if (state.repositories.size > 1) {
                item(key = "filters") {
                    RepositoryFilters(state.repositories, state.repositoryFilter) {
                        onIntent(DashboardIntent.FilterByRepository(it))
                    }
                }
            }

            if (state.sessions.isEmpty()) {
                item(key = "empty") {
                    EmptyState((state.listener as? ListenerState.Listening)?.port)
                }
            } else {
                item(key = "sessions-header") { SectionHeader("Sessões") }
                state.visibleSessions.forEachIndexed { index, session ->
                    item(key = "session-${session.key}") {
                        SessionCard(
                            session = session,
                            now = now,
                            featured = index == 0,
                            onDismiss = { onIntent(DashboardIntent.DismissSession(session.key)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            if (state.visibleTimeline.isNotEmpty()) {
                item(key = "timeline-header") { SectionHeader("Linha do tempo") }
                item(key = "timeline") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.animateContentSize(),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            state.visibleTimeline.take(60).forEachIndexed { i, activity ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                TimelineItem(activity, now, showRepository = state.repositoryFilter == null)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun subtitle(state: DashboardState): String = when {
    state.sessions.isEmpty() -> "Aguardando o Claude…"
    else -> buildString {
        append(state.sessions.size)
        append(if (state.sessions.size == 1) " sessão" else " sessões")
        append(" · ")
        append(state.workingCount)
        append(" trabalhando · ")
        append(state.repositories.size)
        append(if (state.repositories.size == 1) " repositório" else " repositórios")
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun RepositoryFilters(repositories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Todos") })
        }
        items(repositories) { slug ->
            FilterChip(
                selected = selected == slug,
                onClick = { onSelect(if (selected == slug) null else slug) },
                label = { Text(slug.substringAfterLast('/')) },
            )
        }
    }
}

@Composable
private fun ListenerErrorCard(reason: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Servidor de eventos indisponível",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onRetry) { Text("Tentar de novo") }
        }
    }
}
