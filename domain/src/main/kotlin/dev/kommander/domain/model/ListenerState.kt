package dev.kommander.domain.model

/** Estado do canal que recebe eventos do agente (no nosso caso, o servidor HTTP). */
sealed interface ListenerState {
    data object Stopped : ListenerState
    data class Starting(val port: Int) : ListenerState
    data class Listening(val host: String, val port: Int) : ListenerState
    data class Failed(val reason: String) : ListenerState
}
