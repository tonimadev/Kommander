package dev.kommander.presentation.dashboard

import dev.kommander.domain.model.ActivityStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardReducerTest {

    private fun DashboardState.receive(a: dev.kommander.domain.model.AgentActivity) =
        DashboardReducer.reduce(this, DashboardMutation.ActivityReceived(a))

    @Test
    fun `activities of the same session update a single card`() {
        val state = DashboardState()
            .receive(activity(1, ActivityStatus.THINKING))
            .receive(activity(2, ActivityStatus.CODING))

        assertEquals(1, state.sessions.size)
        assertEquals(ActivityStatus.CODING, state.sessions.single().latest.status)
        assertEquals(2, state.sessions.single().eventCount)
        assertEquals(listOf(2L, 1L), state.timeline.map { it.id })
    }

    @Test
    fun `different sessions get their own cards, most recent first`() {
        val state = DashboardState()
            .receive(activity(1, ActivityStatus.CODING, session = "a", repo = "me/api"))
            .receive(activity(2, ActivityStatus.DEPLOYING, session = "b", repo = "me/web"))
            .receive(activity(3, ActivityStatus.TESTING, session = "a", repo = "me/api"))

        assertEquals(listOf("a", "b"), state.sessions.map { it.key })
        assertEquals(2, state.workingCount)
        assertEquals(listOf("me/api", "me/web"), state.repositories)
    }

    @Test
    fun `repository filter narrows sessions and timeline`() {
        val state = DashboardState()
            .receive(activity(1, ActivityStatus.CODING, session = "a", repo = "me/api"))
            .receive(activity(2, ActivityStatus.CODING, session = "b", repo = "me/web"))
            .let { DashboardReducer.reduce(it, DashboardMutation.RepositoryFilterChanged("me/web")) }

        assertEquals(listOf("b"), state.visibleSessions.map { it.key })
        assertEquals(listOf(2L), state.visibleTimeline.map { it.id })
    }

    @Test
    fun `event without repository keeps the last known one`() {
        val state = DashboardState()
            .receive(activity(1, ActivityStatus.CODING))
            .receive(activity(2, ActivityStatus.WAITING_INPUT, repo = null))

        assertEquals("tonimadev/Kommander", state.sessions.single().repository?.slug)
        assertEquals(true, state.sessions.single().needsAttention)
    }

    @Test
    fun `dismissing the only session of the filtered repo resets the filter`() {
        val state = DashboardState()
            .receive(activity(1, ActivityStatus.CODING, session = "a", repo = "me/api"))
            .let { DashboardReducer.reduce(it, DashboardMutation.HistoryCleared) }
            .let { DashboardReducer.reduce(it, DashboardMutation.RepositoryFilterChanged("me/api")) }
            .let { DashboardReducer.reduce(it, DashboardMutation.SessionDismissed("a")) }

        assertEquals(emptyList(), state.sessions)
        assertNull(state.repositoryFilter)
    }

    @Test
    fun `clear history drops timeline and ended sessions only`() {
        val state = DashboardState()
            .receive(activity(1, ActivityStatus.CODING, session = "a"))
            .receive(activity(2, ActivityStatus.SESSION_ENDED, session = "b"))
            .let { DashboardReducer.reduce(it, DashboardMutation.HistoryCleared) }

        assertEquals(emptyList(), state.timeline)
        assertEquals(listOf("a"), state.sessions.map { it.key })
    }

    @Test
    fun `timeline is capped`() {
        var state = DashboardState()
        repeat(DashboardReducer.MAX_TIMELINE + 10) { state = state.receive(activity(it.toLong(), ActivityStatus.CODING)) }
        assertEquals(DashboardReducer.MAX_TIMELINE, state.timeline.size)
        assertEquals(DashboardReducer.MAX_TIMELINE + 10, state.totalEvents)
    }
}
