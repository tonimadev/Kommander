package dev.kommander.presentation.dashboard

/** Função pura: (estado, mutação) -> novo estado. Fácil de testar, sem coroutines. */
internal object DashboardReducer {

    const val MAX_TIMELINE = 200

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

        val updated = previous?.copy(
            latest = activity,
            // Alguns eventos podem vir sem repositório: mantemos o último conhecido.
            repository = activity.repository ?: previous.repository,
            eventCount = previous.eventCount + 1,
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
}
