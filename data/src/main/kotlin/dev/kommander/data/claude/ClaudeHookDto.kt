package dev.kommander.data.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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
    /** PreToolUse / PostToolUse / PostToolUseFailure: liga a chamada ao seu resultado. */
    @SerialName("tool_use_id") val toolUseId: String? = null,
    /** PostToolUse: retorno da ferramenta (objeto para Bash, texto ou lista para outras). */
    @SerialName("tool_response") val toolResponse: JsonElement? = null,
    /** PostToolUseFailure */
    val error: String? = null,
    /** PostToolUseFailure: `true` quando a falha foi você interrompendo. */
    @SerialName("is_interrupt") val isInterrupt: Boolean? = null,
    /** Servidor MCP da ferramenta (nome legível), quando é uma ferramenta MCP. */
    @SerialName("mcp_server") val mcpServer: String? = null,
    /** JSONL da conversa, de onde lemos o que o Claude disse por último. */
    @SerialName("transcript_path") val transcriptPath: String? = null,
    /** Stop: a resposta final do Claude. */
    @SerialName("last_assistant_message") val lastAssistantMessage: String? = null,
    /** UserPromptSubmit */
    val prompt: String? = null,
    /** Notification */
    val message: String? = null,
    /** SessionStart: startup | resume | clear | compact */
    val source: String? = null,
    /** SessionEnd */
    val reason: String? = null,
)
