package dev.kommander.data.claude

import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.ToolOutcome
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Resultado da interpretação de um hook: o que o Claude está fazendo e sobre o quê. */
internal data class HookClassification(
    val status: ActivityStatus,
    val target: String? = null,
    val message: String? = null,
    /** Preenchido quando o hook é o retorno de uma ferramenta. */
    val outcome: ToolOutcome? = null,
    val outcomeDetail: String? = null,
)

/**
 * Traduz eventos de hook do Claude Code em [ActivityStatus]. Função pura e testável.
 * Retorna `null` para eventos que não devem aparecer no dashboard.
 */
internal object ClaudeHookClassifier {

    private const val MAX_TEXT = 140

    /** Saída de teste/build que indica falha mesmo com o comando "dando certo" (exit 0). */
    private val FAILURE_OUTPUT = Regex(
        listOf(
            """BUILD FAILED""", """FAILURE:""", """\bFAILED\b""", """npm ERR!""", """error\[E\d+]""",
            """Traceback \(most recent call last\)""", """^e: """,
            // Contagens só a partir de 1: "0 failed" é saída de sucesso.
            """(?i)\b[1-9]\d* (tests? )?failed\b""", """(?i)\btests? failed\b""", """(?i)\bcompilation failed\b""",
        ).joinToString("|"),
        RegexOption.MULTILINE,
    )

    /** Onde procurar falha na saída: só em comandos cujo resultado importa. */
    private val OUTPUT_CHECKED = setOf(ActivityStatus.TESTING, ActivityStatus.BUILDING, ActivityStatus.DEPLOYING)

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

        "PreToolUse" -> classifyTool(hook.toolName.orEmpty(), hook.toolInput, hook.mcpServer)

        "PostToolUse" -> classifyResult(hook)

        "PostToolUseFailure" -> classifyTool(hook.toolName.orEmpty(), hook.toolInput, hook.mcpServer).copy(
            outcome = ToolOutcome.FAILED,
            outcomeDetail = if (hook.isInterrupt == true) "Interrompido por você" else hook.error?.let(::failureLine),
        )

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

    private fun classifyResult(hook: ClaudeHookDto): HookClassification {
        val base = classifyTool(hook.toolName.orEmpty(), hook.toolInput, hook.mcpServer)
        // Ferramentas que esperam por você (pergunta, aprovação do plano): o retorno tira o
        // Claude da espera, então é um evento novo, não o resultado de uma ação.
        if (base.status == ActivityStatus.WAITING_INPUT) {
            return HookClassification(ActivityStatus.PLANNING, message = "Resposta recebida, continuando")
        }
        val response = hook.toolResponse as? JsonObject
        fun flag(name: String) = (response?.get(name) as? JsonPrimitive)?.booleanOrNull == true

        val detail = when {
            flag("interrupted") -> "Interrompido"
            flag("is_error") || flag("isError") -> hook.toolResponse?.text()?.let(::failureLine) ?: "Erro"
            base.status in OUTPUT_CHECKED -> hook.toolResponse?.text()?.let { out ->
                out.lineSequence().firstOrNull { FAILURE_OUTPUT.containsMatchIn(it) }?.oneLine()
            }
            else -> null
        }
        return base.copy(
            outcome = if (detail == null) ToolOutcome.SUCCEEDED else ToolOutcome.FAILED,
            outcomeDetail = detail,
        )
    }

    fun classifyTool(tool: String, input: JsonObject?, mcpServer: String? = null): HookClassification {
        fun field(name: String) = input?.string(name)

        return when {
            tool in setOf("Edit", "MultiEdit", "Write", "NotebookEdit") -> HookClassification(
                status = ActivityStatus.CODING,
                target = field("file_path")?.shortPath() ?: field("notebook_path")?.shortPath(),
                message = if (tool == "Write") "Criando arquivo" else "Editando código",
            )

            tool in setOf("Read", "Grep", "Glob", "LS", "NotebookRead") -> HookClassification(
                status = ActivityStatus.EXPLORING,
                target = field("file_path")?.shortPath() ?: field("pattern") ?: field("path")?.shortPath(),
                message = "Lendo o código",
            )

            tool in setOf("WebFetch", "WebSearch") -> HookClassification(
                status = ActivityStatus.EXPLORING,
                target = field("url") ?: field("query"),
                message = "Pesquisando na web",
            )

            tool == "ToolSearch" ->
                HookClassification(ActivityStatus.EXPLORING, target = field("query"), message = "Procurando ferramentas")

            tool in setOf("Task", "Agent") -> HookClassification(
                status = ActivityStatus.PLANNING,
                target = field("description"),
                message = "Delegando para um subagente",
            )

            tool == "SendMessage" ->
                HookClassification(ActivityStatus.PLANNING, target = field("to"), message = "Conversando com outro agente")

            tool == "TodoWrite" || tool == "TaskCreate" || tool == "TaskUpdate" ->
                HookClassification(ActivityStatus.PLANNING, message = "Organizando as tarefas")

            tool == "EnterPlanMode" -> HookClassification(ActivityStatus.PLANNING, message = "Entrando no modo de plano")
            tool == "ExitPlanMode" -> HookClassification(ActivityStatus.WAITING_INPUT, message = "Plano pronto para sua aprovação")

            tool == "Skill" ->
                HookClassification(ActivityStatus.PLANNING, target = field("skill"), message = "Carregando uma skill")

            tool == "AskUserQuestion" -> HookClassification(
                status = ActivityStatus.WAITING_INPUT,
                target = input?.firstQuestion()?.oneLine(),
                message = "Perguntando a você",
            )

            tool == "Artifact" ->
                HookClassification(ActivityStatus.CODING, target = field("file_path")?.shortPath(), message = "Publicando uma página")

            tool in setOf("BashOutput", "TaskOutput", "Monitor") ->
                HookClassification(ActivityStatus.RUNNING_COMMAND, message = "Acompanhando comando em segundo plano")

            tool in setOf("KillShell", "TaskStop") ->
                HookClassification(ActivityStatus.RUNNING_COMMAND, message = "Parando comando em segundo plano")

            tool in setOf("ScheduleWakeup", "CronCreate") ->
                HookClassification(ActivityStatus.RUNNING_COMMAND, message = "Agendando a próxima verificação")

            tool.startsWith("mcp__") -> classifyMcpTool(tool, input, mcpServer)

            tool == "Bash" -> BashCommandClassifier.classify(field("command").orEmpty(), field("description"))

            else -> HookClassification(ActivityStatus.RUNNING_COMMAND, target = tool, message = "Usando ferramenta")
        }
    }

    /** `mcp__<servidor>__<ação>`: GitHub tem regras próprias; os demais são classificados pelo verbo da ação. */
    private fun classifyMcpTool(tool: String, input: JsonObject?, mcpServer: String?): HookClassification {
        val serverId = tool.removePrefix("mcp__").substringBefore("__")
        val action = tool.removePrefix("mcp__").substringAfter("__", missingDelimiterValue = "")
        if (serverId.contains("github", ignoreCase = true)) return classifyGitHubTool(action, input)

        val server = mcpServer?.takeIf { it.isNotBlank() } ?: serverId.friendlyServerName()
        val subject = listOf("query", "title", "subject", "name", "url", "goal", "task", "description", "path")
            .firstNotNullOfOrNull { input?.string(it) }
            ?.oneLine()
        val target = subject ?: action.replace('_', ' ')
        val verb = action.lowercase()
        return when {
            verb.contains("deploy") || verb.contains("release") ->
                HookClassification(ActivityStatus.DEPLOYING, target, "Deploy via $server")
            verb.contains("test") || verb.contains("run_task") || verb.contains("diagnose") ->
                HookClassification(ActivityStatus.TESTING, target, "Testando com $server")
            READ_VERBS.any { verb.startsWith(it) || verb.contains("_$it") } ->
                HookClassification(ActivityStatus.EXPLORING, target, "Consultando $server")
            else -> HookClassification(ActivityStatus.RUNNING_COMMAND, target, "Usando $server")
        }
    }

    private val READ_VERBS = listOf(
        "get", "list", "search", "read", "query", "fetch", "find", "inspect", "describe", "view", "lookup", "export",
    )

    /** `claude_ai_Gmail` -> `Gmail`, `plugin_engineering_slack` -> `slack`, `MCP_DOCKER` -> `MCP DOCKER`. */
    private fun String.friendlyServerName(): String {
        val trimmed = when {
            startsWith("claude_ai_") -> removePrefix("claude_ai_")
            startsWith("plugin_") -> substringAfterLast('_')
            else -> this
        }
        return trimmed.replace('_', ' ').ifBlank { this }
    }

    private fun classifyGitHubTool(action: String, input: JsonObject?): HookClassification {
        fun field(name: String) = input?.string(name)
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

    /** A linha mais reveladora de uma saída de erro: a que parece falha, senão a primeira não vazia. */
    private fun failureLine(text: String): String? {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val meaningful = lines.filterNot { it.startsWith("Exit code", ignoreCase = true) }
        return (meaningful.firstOrNull { FAILURE_OUTPUT.containsMatchIn(it) } ?: meaningful.firstOrNull() ?: lines.firstOrNull())
            ?.oneLine()
    }

    /** Texto de um `tool_response`: stdout/stderr do Bash, string pura, ou blocos `{type: text}`. */
    private fun JsonElement.text(): String? = when (this) {
        is JsonPrimitive -> content.takeIf { isString }
        is JsonObject -> listOfNotNull(string("stdout"), string("stderr"), string("error"), string("content"), string("text"))
            .joinToString("\n")
            .ifBlank { (get("content") as? JsonArray)?.text() }
        is JsonArray -> mapNotNull { (it as? JsonObject)?.string("text") ?: (it as? JsonPrimitive)?.text() }
            .joinToString("\n")
            .ifBlank { null }
    }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.firstQuestion(): String? =
        ((get("questions") as? JsonArray)?.firstOrNull() as? JsonObject)?.string("question")

    internal fun String.oneLine(): String {
        val flat = lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        return if (flat.length > MAX_TEXT) flat.take(MAX_TEXT - 1) + "…" else flat
    }

    /** `/home/user/proj/src/main/Foo.kt` -> `src/main/Foo.kt` (últimos 3 segmentos). */
    private fun String.shortPath(): String = split('/').filter { it.isNotEmpty() }.takeLast(3).joinToString("/")
}
