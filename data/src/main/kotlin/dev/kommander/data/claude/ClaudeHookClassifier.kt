package dev.kommander.data.claude

import dev.kommander.domain.model.ActivityStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Resultado da interpretação de um hook: o que o Claude está fazendo e sobre o quê. */
internal data class HookClassification(
    val status: ActivityStatus,
    val target: String? = null,
    val message: String? = null,
)

/**
 * Traduz eventos de hook do Claude Code em [ActivityStatus]. Função pura e testável.
 * Retorna `null` para eventos que não devem aparecer no dashboard (ex.: PostToolUse).
 */
internal object ClaudeHookClassifier {

    private const val MAX_TEXT = 140

    fun classify(hook: ClaudeHookDto): HookClassification? = when (hook.hookEventName) {
        "SessionStart" -> HookClassification(
            status = ActivityStatus.SESSION_STARTED,
            message = when (hook.source) {
                "resume" -> "Sessão retomada"
                "clear" -> "Contexto limpo, nova conversa"
                "compact" -> "Contexto compactado"
                else -> "Sessão iniciada"
            },
        )

        "UserPromptSubmit" -> HookClassification(
            status = ActivityStatus.THINKING,
            target = hook.prompt?.oneLine(),
            message = "Analisando o pedido",
        )

        "PreToolUse" -> classifyTool(hook.toolName.orEmpty(), hook.toolInput)

        "Notification" -> HookClassification(
            status = ActivityStatus.WAITING_INPUT,
            message = hook.message?.oneLine() ?: "Aguardando sua resposta",
        )

        "Stop" -> HookClassification(ActivityStatus.DONE, message = "Terminou, aguardando próximo pedido")

        "SessionEnd" -> HookClassification(
            status = ActivityStatus.SESSION_ENDED,
            message = hook.reason?.let { "Sessão encerrada ($it)" } ?: "Sessão encerrada",
        )

        else -> null
    }

    fun classifyTool(tool: String, input: JsonObject?): HookClassification {
        fun field(name: String) = (input?.get(name) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

        return when {
            tool in setOf("Edit", "MultiEdit", "Write", "NotebookEdit") -> HookClassification(
                status = ActivityStatus.CODING,
                target = field("file_path")?.shortPath() ?: field("notebook_path")?.shortPath(),
                message = if (tool == "Write") "Criando arquivo" else "Editando código",
            )

            tool in setOf("Read", "Grep", "Glob", "LS") -> HookClassification(
                status = ActivityStatus.EXPLORING,
                target = field("file_path")?.shortPath() ?: field("pattern") ?: field("path")?.shortPath(),
                message = "Lendo o código",
            )

            tool in setOf("WebFetch", "WebSearch") -> HookClassification(
                status = ActivityStatus.EXPLORING,
                target = field("url") ?: field("query"),
                message = "Pesquisando na web",
            )

            tool in setOf("Task", "Agent") -> HookClassification(
                status = ActivityStatus.PLANNING,
                target = field("description"),
                message = "Delegando para um subagente",
            )

            tool == "TodoWrite" || tool == "TaskCreate" || tool == "TaskUpdate" ->
                HookClassification(ActivityStatus.PLANNING, message = "Organizando as tarefas")

            tool.startsWith("mcp__github__") -> classifyGitHubTool(tool.removePrefix("mcp__github__"), input)

            tool == "Bash" -> BashCommandClassifier.classify(field("command").orEmpty(), field("description"))

            else -> HookClassification(ActivityStatus.RUNNING_COMMAND, target = tool, message = "Usando ferramenta")
        }
    }

    private fun classifyGitHubTool(action: String, input: JsonObject?): HookClassification {
        fun field(name: String) = (input?.get(name) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val repo = listOfNotNull(field("owner"), field("repo")).takeIf { it.size == 2 }?.joinToString("/")
        val title = field("title")?.oneLine()
        return when {
            action == "create_pull_request" ->
                HookClassification(ActivityStatus.OPENING_PR, target = title ?: repo, message = "Abrindo pull request")
            action.startsWith("issue_write") || action == "create_issue" ->
                HookClassification(ActivityStatus.OPENING_ISSUE, target = title ?: repo, message = "Abrindo/atualizando issue")
            action.contains("review") ->
                HookClassification(ActivityStatus.REVIEWING, target = repo, message = "Revisando pull request")
            action.contains("comment") ->
                HookClassification(ActivityStatus.GITHUB, target = repo, message = "Comentando no GitHub")
            action.startsWith("push_files") || action.startsWith("create_or_update_file") ->
                HookClassification(ActivityStatus.COMMITTING, target = repo, message = "Enviando arquivos ao GitHub")
            else -> HookClassification(
                status = ActivityStatus.GITHUB,
                target = repo,
                message = "GitHub: ${action.replace('_', ' ')}",
            )
        }
    }

    internal fun String.oneLine(): String {
        val flat = lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        return if (flat.length > MAX_TEXT) flat.take(MAX_TEXT - 1) + "…" else flat
    }

    /** `/home/user/proj/src/main/Foo.kt` -> `src/main/Foo.kt` (últimos 3 segmentos). */
    private fun String.shortPath(): String = split('/').filter { it.isNotEmpty() }.takeLast(3).joinToString("/")
}
