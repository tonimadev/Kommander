package dev.kommander.presentation.dashboard

import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.AgentActivity
import dev.kommander.domain.model.ListenerState
import dev.kommander.domain.model.RepositoryRef
import dev.kommander.domain.model.ToolOutcome
import dev.kommander.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

internal fun activity(
    id: Long,
    status: ActivityStatus,
    session: String? = "s1",
    repo: String? = "tonimadev/Kommander",
    tool: String? = null,
    toolUseId: String? = null,
    outcome: ToolOutcome? = null,
    detail: String? = null,
    narration: String? = null,
) = AgentActivity(
    id = id,
    timestamp = Instant.ofEpochSecond(1_000 + id),
    agent = "claude-code",
    sessionId = session,
    repository = repo?.let { RepositoryRef(it, "main") },
    status = status,
    target = null,
    message = null,
    tool = tool,
    toolUseId = toolUseId,
    outcome = outcome,
    outcomeDetail = detail,
    narration = narration,
)

internal class FakeActivityRepository : ActivityRepository {
    val emitter = MutableSharedFlow<AgentActivity>(extraBufferCapacity = 16)
    override val activities = emitter
    override val listenerState = MutableStateFlow<ListenerState>(ListenerState.Stopped)
    var starts = 0
    var stops = 0

    override suspend fun start() {
        starts++
        listenerState.value = ListenerState.Listening("127.0.0.1", 8080)
    }

    override suspend fun stop() {
        stops++
        listenerState.value = ListenerState.Stopped
    }
}
