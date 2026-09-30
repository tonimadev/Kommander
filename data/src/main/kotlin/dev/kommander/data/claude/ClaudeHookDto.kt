package dev.kommander.data.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * JSON que o Claude Code envia no stdin de cada hook. O hook só repassa esse JSON
 * via `curl` para `POST /hooks/claude-code`; toda a interpretação é feita aqui.
 *
 * Referência: https://docs.claude.com/en/docs/claude-code/hooks
 */
@Serializable
data class ClaudeHookDto(
    @SerialName("hook_event_name") val hookEventName: String,
    @SerialName("session_id") val sessionId: String? = null,
    val cwd: String? = null,
    @SerialName("tool_name") val toolName: String? = null,
    @SerialName("tool_input") val toolInput: JsonObject? = null,
    /** UserPromptSubmit */
    val prompt: String? = null,
    /** Notification */
    val message: String? = null,
    /** SessionStart: startup | resume | clear | compact */
    val source: String? = null,
    /** SessionEnd */
    val reason: String? = null,
)
