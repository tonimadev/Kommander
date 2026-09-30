package dev.kommander.data.claude

import dev.kommander.domain.model.ActivityStatus
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
        assertEquals(ActivityStatus.RUNNING_COMMAND, bash("ls -la").status)
    }

    @Test
    fun `lifecycle hooks`() {
        assertEquals(ActivityStatus.SESSION_STARTED, classify("SessionStart")?.status)
        assertEquals(ActivityStatus.THINKING, classify("UserPromptSubmit", prompt = "faça X\ne Y")?.status)
        assertEquals("faça X e Y", classify("UserPromptSubmit", prompt = "faça X\ne Y")?.target)
        assertEquals(ActivityStatus.WAITING_INPUT, classify("Notification", message = "Claude needs permission")?.status)
        assertEquals(ActivityStatus.DONE, classify("Stop")?.status)
        assertEquals(ActivityStatus.SESSION_ENDED, classify("SessionEnd")?.status)
        assertNull(classify("PostToolUse"))
    }

    private fun classify(event: String, prompt: String? = null, message: String? = null) =
        ClaudeHookClassifier.classify(ClaudeHookDto(hookEventName = event, prompt = prompt, message = message))
}
