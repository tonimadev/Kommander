package dev.kommander.presentation.dashboard

import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.ToolOutcome
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

    @Test
    fun `tool result completes the original call instead of adding an event`() {
        val state = DashboardState()
            .receive(activity(1, ActivityStatus.THINKING))
            .receive(activity(2, ActivityStatus.TESTING, tool = "Bash", toolUseId = "t1"))
            .receive(activity(3, ActivityStatus.TESTING, tool = "Bash", toolUseId = "t1", outcome = ToolOutcome.FAILED, detail = "BUILD FAILED"))
        val session = state.sessions.single()

        assertEquals(listOf(2L, 1L), state.timeline.map { it.id })
        assertEquals(2, session.eventCount)
        assertEquals(ToolOutcome.FAILED, session.latest.outcome)
        assertEquals("BUILD FAILED", session.latest.outcomeDetail)
        assertEquals(ToolOutcome.FAILED, state.timeline.first().outcome)
    }

    @Test
    fun `result of an older parallel call does not replace the latest action`() {
        val session = DashboardState()
            .receive(activity(1, ActivityStatus.EXPLORING, tool = "Read", toolUseId = "r1"))
            .receive(activity(2, ActivityStatus.EXPLORING, tool = "Grep", toolUseId = "g1"))
            .receive(activity(3, ActivityStatus.EXPLORING, tool = "Read", toolUseId = "r1", outcome = ToolOutcome.SUCCEEDED))
            .sessions.single()

        assertEquals(2L, session.latest.id)
        assertEquals(ToolOutcome.SUCCEEDED, session.recent.first { it.id == 1L }.outcome)
    }

    @Test
    fun `result after a permission prompt brings the session back to work`() {
        val session = DashboardState()
            .receive(activity(1, ActivityStatus.RUNNING_COMMAND, tool = "Bash", toolUseId = "b1"))
            .receive(activity(2, ActivityStatus.WAITING_INPUT))
            .receive(activity(3, ActivityStatus.RUNNING_COMMAND, tool = "Bash", toolUseId = "b1", outcome = ToolOutcome.SUCCEEDED))
            .sessions.single()

        assertEquals(1L, session.latest.id)
        assertEquals(true, session.isWorking)
    }

    @Test
    fun `result without a matching call is shown as a regular event`() {
        val state = DashboardState()
            .receive(activity(1, ActivityStatus.THINKING))
            .receive(activity(2, ActivityStatus.TESTING, tool = "Bash", toolUseId = "unknown", outcome = ToolOutcome.FAILED))

        assertEquals(listOf(2L, 1L), state.timeline.map { it.id })
        assertEquals(ToolOutcome.FAILED, state.sessions.single().latest.outcome)
    }

    @Test
    fun `narration sticks until a new prompt clears it`() {
        val base = DashboardState()
            .receive(activity(1, ActivityStatus.THINKING))
            .receive(activity(2, ActivityStatus.EXPLORING, narration = "Vou ler o reducer primeiro."))
            .receive(activity(3, ActivityStatus.CODING))
        assertEquals("Vou ler o reducer primeiro.", base.sessions.single().narration)

        val updated = base.receive(activity(4, ActivityStatus.TESTING, tool = "Bash", toolUseId = "t", narration = "Agora os testes."))
        assertEquals("Agora os testes.", updated.sessions.single().narration)

        assertNull(updated.receive(activity(5, ActivityStatus.THINKING)).sessions.single().narration)
    }
}
