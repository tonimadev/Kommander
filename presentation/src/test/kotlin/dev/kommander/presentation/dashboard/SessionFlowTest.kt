package dev.kommander.presentation.dashboard

import dev.kommander.domain.model.ActivityStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionFlowTest {

    private fun sessionOf(vararg statuses: ActivityStatus): SessionSnapshot {
        var state = DashboardState()
        statuses.forEachIndexed { i, status ->
            state = DashboardReducer.reduce(state, DashboardMutation.ActivityReceived(activity(i.toLong(), status)))
        }
        return state.sessions.single()
    }

    private fun SessionSnapshot.states() = flowSteps().associate { it.stage to it.state }

    @Test
    fun `current stage is active and visited ones are done`() {
        val steps = sessionOf(ActivityStatus.THINKING, ActivityStatus.EXPLORING, ActivityStatus.CODING, ActivityStatus.TESTING)
            .states()

        assertEquals(StageState.DONE, steps[FlowStage.PLANNING])
        assertEquals(StageState.DONE, steps[FlowStage.RESEARCH])
        assertEquals(StageState.DONE, steps[FlowStage.CODING])
        assertEquals(StageState.ACTIVE, steps[FlowStage.TESTING])
        assertEquals(StageState.PENDING, steps[FlowStage.GITHUB])
        assertEquals(StageState.PENDING, steps[FlowStage.DEPLOY])
    }

    @Test
    fun `new prompt starts a fresh flow`() {
        val steps = sessionOf(
            ActivityStatus.THINKING, ActivityStatus.CODING, ActivityStatus.DEPLOYING, ActivityStatus.DONE,
            ActivityStatus.THINKING, ActivityStatus.EXPLORING,
        ).states()

        assertEquals(StageState.ACTIVE, steps[FlowStage.RESEARCH])
        assertEquals(StageState.PENDING, steps[FlowStage.CODING])
        assertEquals(StageState.PENDING, steps[FlowStage.DEPLOY])
    }

    @Test
    fun `finished turn keeps its path but nothing is active`() {
        val session = sessionOf(ActivityStatus.THINKING, ActivityStatus.CODING, ActivityStatus.OPENING_PR, ActivityStatus.DONE)
        val steps = session.states()

        assertEquals(StageState.DONE, steps[FlowStage.CODING])
        assertEquals(StageState.DONE, steps[FlowStage.GITHUB])
        assertEquals(0, session.flowSteps().count { it.state == StageState.ACTIVE })
    }

    @Test
    fun `events without prompt do not leak across a previous DONE`() {
        val steps = sessionOf(ActivityStatus.DEPLOYING, ActivityStatus.DONE, ActivityStatus.CODING).states()
        assertEquals(StageState.ACTIVE, steps[FlowStage.CODING])
        assertEquals(StageState.PENDING, steps[FlowStage.DEPLOY])
    }

    @Test
    fun `counts events per stage and caps recent history`() {
        val statuses = Array(DashboardReducer.MAX_RECENT_PER_SESSION + 5) { ActivityStatus.CODING }
        val session = sessionOf(ActivityStatus.THINKING, *statuses)

        assertEquals(DashboardReducer.MAX_RECENT_PER_SESSION, session.recent.size)
        assertEquals(DashboardReducer.MAX_RECENT_PER_SESSION, session.flowSteps().first { it.stage == FlowStage.CODING }.events)
        assertEquals(session.latest, session.recent.first())
    }

}
