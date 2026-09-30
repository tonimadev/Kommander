package dev.kommander.domain.repository

import dev.kommander.domain.model.AgentActivity
import dev.kommander.domain.model.ListenerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Porta (Clean Architecture) implementada pela camada de dados.
 * A apresentação nunca sabe que por trás existe um servidor Ktor.
 */
interface ActivityRepository {
    /** Stream "quente" de atividades recebidas, na ordem de chegada. */
    val activities: Flow<AgentActivity>

    val listenerState: StateFlow<ListenerState>

    suspend fun start()

    suspend fun stop()
}
