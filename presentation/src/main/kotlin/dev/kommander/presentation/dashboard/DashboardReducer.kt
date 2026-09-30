package dev.kommander.presentation.dashboard

import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.AgentActivity

/** Função pura: (estado, mutação) -> novo estado. Fácil de testar, sem coroutines. */
internal object DashboardReducer {

    const val MAX_TIMELINE = 200
    const val MAX_RECENT_PER_SESSION = 60

    fun reduce(state: DashboardState, mutation: DashboardMutation): DashboardState = when (mutation) {
        is DashboardMutation.ActivityReceived -> onActivity(state, mutation)

        is DashboardMutation.ListenerChanged -> state.copy(listener = mutation.state)

        DashboardMutation.HistoryCleared -> state.copy(
            timeline = emptyList(),
            sessions = state.sessions.filterNot { it.hasEnded },
        )

        is DashboardMutation.SessionDismissed -> {
            val remaining = state.sessions.filterNot { it.key == mutation.key }
            val filterStillValid = state.repositoryFilter == null ||
                remaining.any { it.repository?.slug == state.repositoryFilter } ||
                state.timeline.any { it.repository?.slug == state.repositoryFilter }
            state.copy(
                sessions = remaining,
                repositoryFilter = state.repositoryFilter.takeIf { filterStillValid },
            )
        }

        is DashboardMutation.RepositoryFilterChanged -> state.copy(repositoryFilter = mutation.slug)
    }

    private fun onActivity(state: DashboardState, mutation: DashboardMutation.ActivityReceived): DashboardState {
        val activity = mutation.activity
        val key = activity.sessionKey
        val previous = state.sessions.firstOrNull { it.key == key }

        if (activity.outcome != null && previous != null) {
            previous.recent.firstOrNull { it.isCallAnsweredBy(activity) }
                ?.let { call -> return onToolResult(state, previous, call, activity) }
        }

        val updated = previous?.copy(
            latest = activity,
            // Alguns eventos podem vir sem repositório: mantemos o último conhecido.
            repository = activity.repository ?: previous.repository,
            eventCount = previous.eventCount + 1,
            recent = (listOf(activity) + previous.recent).take(MAX_RECENT_PER_SESSION),
            // Prompt novo apaga a fala do pedido anterior; nos demais eventos vale a mais recente conhecida.
            narration = if (activity.status in TURN_START) activity.narration else activity.narration ?: previous.narration,
        ) ?: SessionSnapshot(
            key = key,
            agent = activity.agent,
            repository = activity.repository,
            latest = activity,
            startedAt = activity.timestamp,
            eventCount = 1,
        )

        return state.copy(
            sessions = listOf(updated) + state.sessions.filterNot { it.key == key },
            timeline = (listOf(activity) + state.timeline).take(MAX_TIMELINE),
            totalEvents = state.totalEvents + 1,
        )
    }

    /** Chamada ainda sem resultado que [result] responde: pelo `tool_use_id`, ou pela ferramenta se não houver id. */
    private fun AgentActivity.isCallAnsweredBy(result: AgentActivity): Boolean {
        if (outcome != null) return false
        return if (result.toolUseId != null) toolUseId == result.toolUseId else tool != null && tool == result.tool
    }

    /**
     * O resultado de uma ferramenta não é uma ação nova: ele completa a chamada original
     * (na sessão e na timeline), que passa a saber se deu certo ou falhou.
     */
    private fun onToolResult(
        state: DashboardState,
        session: SessionSnapshot,
        call: AgentActivity,
        result: AgentActivity,
    ): DashboardState {
        val completed = call.copy(
            outcome = result.outcome,
            outcomeDetail = result.outcomeDetail,
            narration = result.narration ?: call.narration,
        )
        fun List<AgentActivity>.withCompleted() = map { if (it.id == call.id) completed else it }

        val latest = when {
            session.latest.id == call.id -> completed
            // Você aprovou a permissão e a ferramenta rodou: o Claude voltou a trabalhar.
            session.latest.status == ActivityStatus.WAITING_INPUT -> completed
            else -> session.latest
        }
        val updated = session.copy(
            latest = latest,
            repository = result.repository ?: session.repository,
            recent = session.recent.withCompleted(),
            narration = result.narration ?: session.narration,
        )
        return state.copy(
            sessions = listOf(updated) + state.sessions.filterNot { it.key == session.key },
            timeline = state.timeline.withCompleted(),
            totalEvents = state.totalEvents + 1,
        )
    }
}
