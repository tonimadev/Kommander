package dev.kommander.presentation.dashboard

import dev.kommander.domain.model.ActivityCategory
import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.AgentActivity

/** Etapas do "trilho" que a UI desenha para o pedido em andamento. */
enum class FlowStage(val category: ActivityCategory) {
    PLANNING(ActivityCategory.PLANNING),
    RESEARCH(ActivityCategory.RESEARCH),
    CODING(ActivityCategory.CODING),
    TESTING(ActivityCategory.TESTING),
    GITHUB(ActivityCategory.GITHUB),
    DEPLOY(ActivityCategory.DEPLOY),
}

enum class StageState { PENDING, DONE, ACTIVE }

data class FlowStep(val stage: FlowStage, val state: StageState, val events: Int)

private val TURN_START = setOf(ActivityStatus.THINKING, ActivityStatus.SESSION_STARTED)
private val TURN_END = setOf(ActivityStatus.DONE, ActivityStatus.SUCCESS, ActivityStatus.SESSION_ENDED)

/**
 * Atividades do pedido atual (turno), mais recente primeiro: da última mensagem do
 * usuário (`THINKING`) até agora. Um `DONE` anterior também encerra o turno, para
 * que eventos do `/events` sem `THINKING` não misturem pedidos diferentes.
 */
fun SessionSnapshot.currentTurn(): List<AgentActivity> {
    val turn = mutableListOf<AgentActivity>()
    for ((index, activity) in recent.withIndex()) {
        if (index > 0 && activity.status in TURN_END) break
        turn += activity
        if (activity.status in TURN_START) break
    }
    return turn
}

/** Estado de cada etapa do trilho: feita neste turno, ativa agora ou pendente. */
fun SessionSnapshot.flowSteps(): List<FlowStep> {
    val counts = currentTurn().groupingBy { it.status.category }.eachCount()
    val active = latest.status.takeIf { it.isInProgress }?.category
    return FlowStage.entries.map { stage ->
        val events = counts[stage.category] ?: 0
        val state = when {
            stage.category == active -> StageState.ACTIVE
            events > 0 -> StageState.DONE
            else -> StageState.PENDING
        }
        FlowStep(stage, state, events)
    }
}
