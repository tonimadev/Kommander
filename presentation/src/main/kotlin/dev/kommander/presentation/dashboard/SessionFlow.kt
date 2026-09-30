package dev.kommander.presentation.dashboard

import dev.kommander.domain.model.ActivityCategory
import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.AgentActivity
import dev.kommander.domain.model.ToolOutcome

/** Tipos de etapa que podem aparecer no trilho do pedido em andamento. */
enum class FlowStage(val category: ActivityCategory) {
    PLANNING(ActivityCategory.PLANNING),
    RESEARCH(ActivityCategory.RESEARCH),
    CODING(ActivityCategory.CODING),
    COMMAND(ActivityCategory.COMMAND),
    TESTING(ActivityCategory.TESTING),
    GITHUB(ActivityCategory.GITHUB),
    DEPLOY(ActivityCategory.DEPLOY),
    WAITING(ActivityCategory.WAITING),
    ERROR(ActivityCategory.ERROR),
    ;

    companion object {
        /** `null` para categorias que não são etapa de trabalho (concluído, ocioso). */
        fun of(category: ActivityCategory): FlowStage? = entries.firstOrNull { it.category == category }
    }
}

enum class StageState { DONE, ACTIVE }

/**
 * Um trecho do pedido: ações consecutivas do mesmo tipo viram uma etapa só.
 *
 * @param events quantas ações o trecho teve (ex.: 5 leituras seguidas).
 * @param outcome resultado da última ação do trecho que já terminou: se a última execução
 *   dos testes falhou, a etapa fica marcada como falha até o Claude rodá-los de novo.
 * @param detail o motivo da falha, quando houver.
 */
data class FlowStep(
    val stage: FlowStage,
    val state: StageState,
    val events: Int,
    val outcome: ToolOutcome? = null,
    val detail: String? = null,
)

internal val TURN_START = setOf(ActivityStatus.THINKING, ActivityStatus.SESSION_STARTED)
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

/**
 * O caminho que o Claude percorreu neste pedido, na ordem em que aconteceu:
 * `Plano → Leitura → Código → Testes ✗ → Código → Testes ✓`. A última etapa fica
 * ativa enquanto ele trabalha (ou espera você) nela.
 */
fun SessionSnapshot.flowSteps(): List<FlowStep> {
    val steps = mutableListOf<FlowStep>()
    for (activity in currentTurn().asReversed()) {
        val stage = FlowStage.of(activity.status.category) ?: continue
        val last = steps.lastOrNull()
        if (last?.stage == stage) {
            // Só chamadas de ferramenta têm resultado; eventos sem ferramenta mantêm o anterior.
            val fromTool = activity.tool != null
            steps[steps.lastIndex] = last.copy(
                events = last.events + 1,
                outcome = if (fromTool) activity.outcome else last.outcome,
                detail = if (fromTool) activity.outcomeDetail else last.detail,
            )
        } else {
            steps += FlowStep(stage, StageState.DONE, events = 1, activity.outcome, activity.outcomeDetail)
        }
    }

    val live = (isWorking || needsAttention) && FlowStage.of(latest.status.category) == steps.lastOrNull()?.stage
    if (live) steps[steps.lastIndex] = steps.last().copy(state = StageState.ACTIVE)
    return steps
}
