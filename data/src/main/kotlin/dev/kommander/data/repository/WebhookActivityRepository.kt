package dev.kommander.data.repository

import dev.kommander.data.claude.ClaudeHookDto
import dev.kommander.data.claude.FileTranscriptReader
import dev.kommander.data.claude.TranscriptReader
import dev.kommander.data.claude.startsTurn
import dev.kommander.data.claude.toDomain
import dev.kommander.data.git.FileSystemRepositoryResolver
import dev.kommander.data.git.RepositoryResolver
import dev.kommander.data.remote.ActivityEventDto
import dev.kommander.data.remote.EventIngestor
import dev.kommander.data.remote.WebhookServerConfig
import dev.kommander.data.remote.eventsModule
import dev.kommander.data.remote.toDomain
import dev.kommander.domain.model.AgentActivity
import dev.kommander.domain.model.ListenerState
import dev.kommander.domain.model.RepositoryRef
import dev.kommander.domain.repository.ActivityRepository
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementação de [ActivityRepository] baseada em um Ktor Embedded Server (engine CIO).
 *
 * Cada POST vira um [AgentActivity] emitido em um [MutableSharedFlow]. O buffer
 * descarta os eventos mais antigos se a UI não acompanhar, então a requisição HTTP
 * (e, por consequência, o hook do Claude Code) nunca fica bloqueada.
 */
class WebhookActivityRepository(
    private val config: WebhookServerConfig = WebhookServerConfig(),
    private val repositoryResolver: RepositoryResolver = FileSystemRepositoryResolver(),
    private val transcriptReader: TranscriptReader = FileTranscriptReader(),
) : ActivityRepository {

    private val _activities = MutableSharedFlow<AgentActivity>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val activities: Flow<AgentActivity> = _activities.asSharedFlow()

    private val _listenerState = MutableStateFlow<ListenerState>(ListenerState.Stopped)
    override val listenerState: StateFlow<ListenerState> = _listenerState.asStateFlow()

    private val nextId = AtomicLong(0)
    private val lifecycle = Mutex()
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    private val ingestor = object : EventIngestor {
        override suspend fun onEvent(event: ActivityEventDto): Long {
            val activity = event.toDomain(id = nextId.incrementAndGet(), resolvedFromCwd = resolve(event.cwd))
            _activities.emit(activity)
            return activity.id
        }

        override suspend fun onClaudeHook(hook: ClaudeHookDto): Long? {
            val activity = hook.toDomain(
                id = nextId.incrementAndGet(),
                repository = resolve(hook.cwd),
                narration = narrationOf(hook),
            ) ?: return null
            _activities.emit(activity)
            return activity.id
        }
    }

    private suspend fun narrationOf(hook: ClaudeHookDto): String? {
        val path = hook.transcriptPath
        if (hook.startsTurn || hook.lastAssistantMessage != null || path.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) { runCatching { transcriptReader.lastAssistantText(path) }.getOrNull() }
    }

    private suspend fun resolve(cwd: String?): RepositoryRef? {
        if (cwd.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) { runCatching { repositoryResolver.resolve(cwd) }.getOrNull() }
    }

    override suspend fun start() = lifecycle.withLock {
        if (server != null) return@withLock
        _listenerState.value = ListenerState.Starting(config.port)

        val candidate = embeddedServer(CIO, host = config.host, port = config.port) {
            eventsModule(ingestor, config.path)
        }
        try {
            candidate.startSuspend(wait = false)
            server = candidate
            _listenerState.value = ListenerState.Listening(config.host, config.port)
        } catch (e: Exception) {
            // O Ktor embrula falhas de bind (porta ocupada) em CancellationException do job
            // interno do servidor. Só propagamos se quem foi cancelado foi o chamador.
            currentCoroutineContext().ensureActive()
            runCatching { candidate.stop(0, 0) }
            _listenerState.value = ListenerState.Failed(e.rootMessage() ?: "Could not bind ${config.host}:${config.port}")
        }
    }

    override suspend fun stop() = lifecycle.withLock {
        server?.stopSuspend(gracePeriodMillis = 200, timeoutMillis = 1_000)
        server = null
        _listenerState.value = ListenerState.Stopped
    }
}

private fun Throwable.rootMessage(): String? {
    var current: Throwable = this
    while (current.cause != null && current.cause !== current) current = current.cause!!
    return current.message?.let { "${current::class.simpleName}: $it" }
}
