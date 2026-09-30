package dev.kommander.data.claude

import dev.kommander.domain.model.AgentActivity
import dev.kommander.domain.model.RepositoryRef
import java.time.Instant

internal const val CLAUDE_CODE_AGENT = "claude-code"

/** Converte um hook do Claude Code em [AgentActivity], ou `null` se o evento não interessa à UI. */
internal fun ClaudeHookDto.toDomain(
    id: Long,
    repository: RepositoryRef?,
    /** O que o Claude disse por último (texto cru, em markdown), se já foi lido. */
    narration: String? = null,
    now: () -> Instant = Instant::now,
): AgentActivity? {
    val classification = ClaudeHookClassifier.classify(this) ?: return null
    return AgentActivity(
        id = id,
        timestamp = now(),
        agent = CLAUDE_CODE_AGENT,
        sessionId = sessionId?.takeIf { it.isNotBlank() },
        repository = repository,
        status = classification.status,
        target = classification.target,
        message = classification.message,
        tool = toolName,
        toolUseId = toolUseId?.takeIf { it.isNotBlank() },
        outcome = classification.outcome,
        outcomeDetail = classification.outcomeDetail,
        narration = (lastAssistantMessage ?: narration)?.toNarration(),
    )
}

/** Eventos que abrem um pedido novo: o texto anterior do Claude não vale mais. */
internal val ClaudeHookDto.startsTurn: Boolean
    get() = hookEventName == "UserPromptSubmit" || hookEventName == "SessionStart"
