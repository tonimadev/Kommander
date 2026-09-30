package dev.kommander.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Contrato do webhook genérico (`POST /events`), usado pelo seu script de automação:
 *
 * ```json
 * { "timestamp": "2026-09-30T12:00:00Z", "agent": "claude-code",
 *   "status": "DEPLOYING", "target": "Magalu Cloud", "message": "Subindo os containers...",
 *   "repository": "tonimadev/Kommander", "branch": "main", "session_id": "abc" }
 * ```
 *
 * `repository`, `branch`, `session_id` e `cwd` são opcionais. Se só `cwd` vier, o
 * repositório é descoberto lendo o `.git` daquela pasta.
 */
@Serializable
data class ActivityEventDto(
    val timestamp: String? = null,
    val agent: String? = null,
    val status: String,
    val target: String? = null,
    val message: String? = null,
    val repository: String? = null,
    val branch: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val cwd: String? = null,
    val tool: String? = null,
)

@Serializable
data class EventAcceptedResponse(val accepted: Boolean, val id: Long? = null)

@Serializable
data class ErrorResponse(val error: String)
