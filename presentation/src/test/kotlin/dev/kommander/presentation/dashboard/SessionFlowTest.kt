package dev.kommander.presentation.dashboard

import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.AgentActivity
import dev.kommander.domain.model.ToolOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionFlowTest {

    private fun sessionOf(vararg activities: AgentActivity): SessionSnapshot {
        var state = DashboardState()
        activities.forEach { state = DashboardReducer.reduce(state, DashboardMutation.ActivityReceived(it)) }
        return state.sessions.single()
    }

    private fun sessionOf(vararg statuses: ActivityStatus): SessionSnapshot =
        sessionOf(*statuses.mapIndexed { i, status -> activity(i.toLong(), status) }.toTypedArray())

    private fun SessionSnapshot.path() = flowSteps().map { it.stage }

    @Test
    fun `rail follows the real order of the turn, merging consecutive actions`() {
        val session = sessionOf(
            ActivityStatus.THINKING, ActivityStatus.EXPLORING, ActivityStatus.EXPLORING,
            ActivityStatus.CODING, ActivityStatus.TESTING, ActivityStatus.CODING, ActivityStatus.TESTING,
        )

        assertEquals(
            listOf(FlowStage.PLANNING, FlowStage.RESEARCH, FlowStage.CODING, FlowStage.TESTING, FlowStage.CODING, FlowStage.TESTING),
            session.path(),
        )
        assertEquals(2, session.flowSteps()[1].events)
    }

    @Test
    fun `only the last step is active while working`() {
        val steps = sessionOf(ActivityStatus.THINKING, ActivityStatus.CODING, ActivityStatus.TESTING).flowSteps()

        assertEquals(listOf(StageState.DONE, StageState.DONE, StageState.ACTIVE), steps.map { it.state })
    }

    @Test
    fun `new prompt starts a fresh rail`() {
        val session = sessionOf(
            ActivityStatus.THINKING, ActivityStatus.CODING, ActivityStatus.DEPLOYING, ActivityStatus.DONE,
            ActivityStatus.THINKING, ActivityStatus.EXPLORING,
        )
        assertEquals(listOf(FlowStage.PLANNING, FlowStage.RESEARCH), session.path())
    }

    @Test
    fun `finished turn keeps its path but nothing is active`() {
        val session = sessionOf(ActivityStatus.THINKING, ActivityStatus.CODING, ActivityStatus.OPENING_PR, ActivityStatus.DONE)

        assertEquals(listOf(FlowStage.PLANNING, FlowStage.CODING, FlowStage.GITHUB), session.path())
        assertEquals(0, session.flowSteps().count { it.state == StageState.ACTIVE })
    }

    @Test
    fun `waiting for you is a step of its own and pulses`() {
        val steps = sessionOf(ActivityStatus.THINKING, ActivityStatus.CODING, ActivityStatus.WAITING_INPUT).flowSteps()

        assertEquals(FlowStage.WAITING, steps.last().stage)
        assertEquals(StageState.ACTIVE, steps.last().state)
    }

    @Test
    fun `events without prompt do not leak across a previous DONE`() {
        val session = sessionOf(ActivityStatus.DEPLOYING, ActivityStatus.DONE, ActivityStatus.CODING)
        assertEquals(listOf(FlowStage.CODING), session.path())
    }

    @Test
    fun `failed run marks the step, a later passing run clears it`() {
        val failed = sessionOf(
            activity(1, ActivityStatus.THINKING),
            activity(2, ActivityStatus.TESTING, tool = "Bash", toolUseId = "t1"),
            activity(3, ActivityStatus.TESTING, tool = "Bash", toolUseId = "t1", outcome = ToolOutcome.FAILED, detail = "2 tests failed"),
        )
        val testing = failed.flowSteps().last()
        assertEquals(ToolOutcome.FAILED, testing.outcome)
        assertEquals("2 tests failed", testing.detail)

        val fixed = sessionOf(
            activity(1, ActivityStatus.THINKING),
            activity(2, ActivityStatus.TESTING, tool = "Bash", toolUseId = "t1"),
            activity(3, ActivityStatus.TESTING, tool = "Bash", toolUseId = "t1", outcome = ToolOutcome.FAILED),
            activity(4, ActivityStatus.CODING, tool = "Edit", toolUseId = "e1"),
            activity(5, ActivityStatus.TESTING, tool = "Bash", toolUseId = "t2"),
            activity(6, ActivityStatus.TESTING, tool = "Bash", toolUseId = "t2", outcome = ToolOutcome.SUCCEEDED),
        ).flowSteps()
        assertEquals(listOf(ToolOutcome.FAILED, ToolOutcome.SUCCEEDED), fixed.filter { it.stage == FlowStage.TESTING }.map { it.outcome })
    }

    @Test
    fun `a rerun in progress is not shown as failed yet`() {
        val steps = sessionOf(
            activity(1, ActivityStatus.THINKING),
            activity(2, ActivityStatus.TESTING, tool = "Bash", toolUseId = "t1"),
            activity(3, ActivityStatus.TESTING, tool = "Bash", toolUseId = "t1", outcome = ToolOutcome.FAILED),
            activity(4, ActivityStatus.TESTING, tool = "Bash", toolUseId = "t2"),
        ).flowSteps()

        assertEquals(2, steps.last().events)
        assertNull(steps.last().outcome)
    }

    @Test
    fun `caps recent history`() {
        val statuses = Array(DashboardReducer.MAX_RECENT_PER_SESSION + 5) { ActivityStatus.CODING }
        val session = sessionOf(ActivityStatus.THINKING, *statuses)

        assertEquals(DashboardReducer.MAX_RECENT_PER_SESSION, session.recent.size)
        assertEquals(DashboardReducer.MAX_RECENT_PER_SESSION, session.flowSteps().single().events)
        assertEquals(session.latest, session.recent.first())
    }
}
