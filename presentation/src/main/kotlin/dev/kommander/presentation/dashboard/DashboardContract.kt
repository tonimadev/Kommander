package dev.kommander.presentation.dashboard

import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.AgentActivity
import dev.kommander.domain.model.ListenerState
import dev.kommander.domain.model.RepositoryRef
import java.time.Instant

/**
 * Uma sessão de trabalho do agente (tipicamente uma sessão do Claude Code em um repositório).
 * O dashboard mostra um card por sessão com a atividade mais recente.
 */
data class SessionSnapshot(
    val key: String,
    val agent: String,
    val repository: RepositoryRef?,
    val latest: AgentActivity,
    val startedAt: Instant,
    val eventCount: Int,
    /** Últimas atividades desta sessão, mais recente primeiro (alimenta o fluxo e a linha de pulso). */
    val recent: List<AgentActivity> = listOf(latest),
    /** O que o Claude disse por último neste pedido (some quando você manda um prompt novo). */
    val narration: String? = latest.narration,
) {
    val isWorking: Boolean get() = latest.status.isInProgress
    val needsAttention: Boolean get() = latest.status == ActivityStatus.WAITING_INPUT
    val hasEnded: Boolean get() = latest.status == ActivityStatus.SESSION_ENDED
}

/** Estado único e imutável da tela (a "Model" do MVI). */
data class DashboardState(
    /** Mais recente primeiro. */
    val sessions: List<SessionSnapshot> = emptyList(),
    /** Todas as atividades, mais recente primeiro. */
    val timeline: List<AgentActivity> = emptyList(),
    /** `null` = todos os repositórios. */
    val repositoryFilter: String? = null,
    val listener: ListenerState = ListenerState.Stopped,
    val totalEvents: Int = 0,
) {
    val workingCount: Int get() = sessions.count { it.isWorking }

    /** Repositórios conhecidos, para os chips de filtro. */
    val repositories: List<String>
        get() = (sessions.mapNotNull { it.repository?.slug } + timeline.mapNotNull { it.repository?.slug }).distinct()

    val visibleSessions: List<SessionSnapshot>
        get() = repositoryFilter?.let { f -> sessions.filter { it.repository?.slug == f } } ?: sessions

    val visibleTimeline: List<AgentActivity>
        get() = repositoryFilter?.let { f -> timeline.filter { it.repository?.slug == f } } ?: timeline
}

/** Intenções disparadas pela UI. */
sealed interface DashboardIntent {
    data object StartListening : DashboardIntent
    data object RetryListening : DashboardIntent
    data object ClearHistory : DashboardIntent
    data class DismissSession(val key: String) : DashboardIntent
    data class FilterByRepository(val slug: String?) : DashboardIntent
}

/** Resultados internos (vindos da UI ou da camada de dados) que o reducer aplica ao estado. */
internal sealed interface DashboardMutation {
    data class ActivityReceived(val activity: AgentActivity) : DashboardMutation
    data class ListenerChanged(val state: ListenerState) : DashboardMutation
    data object HistoryCleared : DashboardMutation
    data class SessionDismissed(val key: String) : DashboardMutation
    data class RepositoryFilterChanged(val slug: String?) : DashboardMutation
}
