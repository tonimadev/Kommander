package dev.kommander.data.claude

import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.ToolOutcome
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClaudeHookClassifierTest {

    private fun bash(command: String) = ClaudeHookClassifier.classifyTool(
        "Bash",
        buildJsonObject { put("command", JsonPrimitive(command)) },
    )

    @Test
    fun `edit tools are CODING with short file path`() {
        val result = ClaudeHookClassifier.classifyTool(
            "Edit",
            buildJsonObject { put("file_path", JsonPrimitive("/home/me/proj/src/main/Foo.kt")) },
        )
        assertEquals(ActivityStatus.CODING, result.status)
        assertEquals("src/main/Foo.kt", result.target)
    }

    @Test
    fun `read tools are EXPLORING`() {
        assertEquals(ActivityStatus.EXPLORING, ClaudeHookClassifier.classifyTool("Grep", null).status)
    }

    @Test
    fun `github mcp tools are GITHUB family`() {
        val pr = ClaudeHookClassifier.classifyTool(
            "mcp__github__create_pull_request",
            buildJsonObject {
                put("owner", JsonPrimitive("tonimadev"))
                put("repo", JsonPrimitive("Kommander"))
                put("title", JsonPrimitive("Add dashboard"))
            },
        )
        assertEquals(ActivityStatus.OPENING_PR, pr.status)
        assertEquals("Add dashboard", pr.target)
        assertEquals(ActivityStatus.GITHUB, ClaudeHookClassifier.classifyTool("mcp__github__list_branches", null).status)
    }

    @Test
    fun `bash commands are classified by intent`() {
        assertEquals(ActivityStatus.DEPLOYING, bash("mgc container-registry push img:1").status)
        assertEquals("Magalu Cloud", bash("mgc kubernetes cluster list").target)
        assertEquals(ActivityStatus.DEPLOYING, bash("kubectl apply -f k8s/").status)
        assertEquals(ActivityStatus.OPENING_PR, bash("gh pr create --fill").status)
        assertEquals(ActivityStatus.COMMITTING, bash("git add . && git commit -m 'x'").status)
        assertEquals(ActivityStatus.COMMITTING, bash("git push -u origin main").status)
        assertEquals(ActivityStatus.TESTING, bash("./gradlew :data:test").status)
        assertEquals(ActivityStatus.TESTING, bash("npm test").status)
        assertEquals(ActivityStatus.BUILDING, bash("./gradlew build").status)
        assertEquals(ActivityStatus.RUNNING_COMMAND, bash("./scripts/simulate.sh").status)
    }

    @Test
    fun `lifecycle hooks`() {
        assertEquals(ActivityStatus.SESSION_STARTED, classify("SessionStart")?.status)
        assertEquals(ActivityStatus.THINKING, classify("UserPromptSubmit", prompt = "faça X\ne Y")?.status)
        assertEquals("faça X e Y", classify("UserPromptSubmit", prompt = "faça X\ne Y")?.target)
        assertEquals(ActivityStatus.WAITING_INPUT, classify("Notification", message = "Claude needs permission")?.status)
        assertEquals(ActivityStatus.DONE, classify("Stop")?.status)
        assertEquals(ActivityStatus.SESSION_ENDED, classify("SessionEnd")?.status)
        assertNull(classify("SubagentStop"))
    }

    private fun classify(event: String, prompt: String? = null, message: String? = null) =
        ClaudeHookClassifier.classify(ClaudeHookDto(hookEventName = event, prompt = prompt, message = message))

    @Test
    fun `more bash intents are recognized`() {
        assertEquals(ActivityStatus.TESTING, bash("./gradlew ktlintCheck").status)
        assertEquals("Verificando o código (lint)", bash("npx eslint src").message)
        assertEquals("Instalando dependências", bash("npm install").message)
        assertEquals(ActivityStatus.DEPLOYING, bash("adb -s emulator-5554 install app.apk").status)
        assertEquals(ActivityStatus.EXPLORING, bash("adb devices -l").status)
        assertEquals("Consultando o git", bash("cd /repo; git log --oneline").message)
        assertEquals(ActivityStatus.COMMITTING, bash("git status && git push").status)
        assertEquals("Fazendo uma requisição HTTP", bash("curl -s http://localhost:8080/health").message)
        assertEquals("Lendo arquivos", bash("cd /repo && cat README.md").message)
        assertEquals("Lendo arquivos", bash("ls -la").message)
        assertEquals("Executando a aplicação", bash("./gradlew :desktopApp:run").message)
        assertEquals(ActivityStatus.RUNNING_COMMAND, bash("python3 script.py").status)
    }

    @Test
    fun `generic mcp tools use the server name and the action verb`() {
        val search = ClaudeHookClassifier.classifyTool(
            "mcp__claude_ai_Gmail__search_threads",
            buildJsonObject { put("query", JsonPrimitive("from:ci")) },
        )
        assertEquals(ActivityStatus.EXPLORING, search.status)
        assertEquals("Consultando Gmail", search.message)
        assertEquals("from:ci", search.target)

        val run = ClaudeHookClassifier.classifyTool("mcp__artemis__mobile_run_task", null, mcpServer = "artemis")
        assertEquals(ActivityStatus.TESTING, run.status)
        assertEquals("mobile run task", run.target)

        assertEquals("Usando slack", ClaudeHookClassifier.classifyTool("mcp__plugin_engineering_slack__send_message", null).message)
        assertEquals(ActivityStatus.OPENING_PR, ClaudeHookClassifier.classifyTool("mcp__plugin_x_github__create_pull_request", null).status)
    }

    @Test
    fun `built-in tools beyond files and bash`() {
        val skill = ClaudeHookClassifier.classifyTool("Skill", buildJsonObject { put("skill", JsonPrimitive("code-review")) })
        assertEquals(ActivityStatus.PLANNING, skill.status)
        assertEquals("code-review", skill.target)

        val question = ClaudeHookClassifier.classifyTool(
            "AskUserQuestion",
            buildJsonObject {
                put("questions", buildJsonArray { add(buildJsonObject { put("question", JsonPrimitive("Qual device?")) }) })
            },
        )
        assertEquals(ActivityStatus.WAITING_INPUT, question.status)
        assertEquals("Qual device?", question.target)
        assertEquals(ActivityStatus.WAITING_INPUT, ClaudeHookClassifier.classifyTool("ExitPlanMode", null).status)
    }

    private fun result(event: String, command: String, response: JsonElement? = null, error: String? = null, interrupt: Boolean? = null) =
        ClaudeHookClassifier.classify(
            ClaudeHookDto(
                hookEventName = event,
                toolName = "Bash",
                toolInput = buildJsonObject { put("command", JsonPrimitive(command)) },
                toolUseId = "toolu_1",
                toolResponse = response,
                error = error,
                isInterrupt = interrupt,
            ),
        )!!

    private fun output(stdout: String) = buildJsonObject {
        put("stdout", JsonPrimitive(stdout))
        put("stderr", JsonPrimitive(""))
        put("interrupted", JsonPrimitive(false))
    }

    @Test
    fun `PostToolUse reports success, or failure found in test output`() {
        val ok = result("PostToolUse", "npm test", output("Tests: 12 passed, 0 failed"))
        assertEquals(ActivityStatus.TESTING, ok.status)
        assertEquals(ToolOutcome.SUCCEEDED, ok.outcome)

        val broken = result("PostToolUse", "./gradlew test", output("> Task :data:test\n3 tests completed, 1 failed\nBUILD FAILED in 4s"))
        assertEquals(ToolOutcome.FAILED, broken.outcome)
        assertEquals("3 tests completed, 1 failed", broken.outcomeDetail)

        // Em comandos comuns a saída não é inspecionada: "FAILED" num log lido é só texto.
        assertEquals(ToolOutcome.SUCCEEDED, result("PostToolUse", "cat build.log", output("BUILD FAILED")).outcome)
        assertEquals(ToolOutcome.FAILED, result("PostToolUse", "sleep 100", buildJsonObject { put("interrupted", JsonPrimitive(true)) }).outcome)
    }

    @Test
    fun `PostToolUseFailure carries the most telling error line`() {
        val failure = result("PostToolUseFailure", "./gradlew test", error = "Exit code 1\n\n> Task :test FAILED\nmore output")
        assertEquals(ActivityStatus.TESTING, failure.status)
        assertEquals(ToolOutcome.FAILED, failure.outcome)
        assertEquals("> Task :test FAILED", failure.outcomeDetail)

        assertEquals("Interrompido por você", result("PostToolUseFailure", "sleep 100", interrupt = true).outcomeDetail)
    }

    @Test
    fun `answering a question is a new event, not a tool result`() {
        val answered = ClaudeHookClassifier.classify(ClaudeHookDto(hookEventName = "PostToolUse", toolName = "AskUserQuestion"))!!
        assertEquals(ActivityStatus.PLANNING, answered.status)
        assertNull(answered.outcome)
    }
}
