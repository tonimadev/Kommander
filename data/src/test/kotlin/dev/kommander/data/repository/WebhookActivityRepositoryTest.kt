package dev.kommander.data.repository

import dev.kommander.data.remote.WebhookServerConfig
import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.ListenerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WebhookActivityRepositoryTest {

    private fun freePort() = ServerSocket(0).use { it.localPort }

    @Test
    fun `real HTTP POST is emitted on the activities flow`() = runBlocking {
        val port = freePort()
        val repository = WebhookActivityRepository(WebhookServerConfig(port = port))
        repository.start()
        try {
            assertIs<ListenerState.Listening>(repository.listenerState.value)

            val next = async { withTimeout(5_000) { repository.activities.first() } }
            val status = withContext(Dispatchers.IO) {
                HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:$port/events"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""{"status":"CODING","agent":"claude-code"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.discarding(),
                ).statusCode()
            }

            assertEquals(202, status)
            assertEquals(ActivityStatus.CODING, next.await().status)
        } finally {
            repository.stop()
        }
        assertEquals(ListenerState.Stopped, repository.listenerState.value)
    }

    @Test
    fun `Claude Code hook is resolved to its git repository`() = runBlocking {
        val repoDir = Files.createTempDirectory("kommander-e2e").toFile()
        try {
            File(repoDir, ".git").mkdirs()
            File(repoDir, ".git/HEAD").writeText("ref: refs/heads/main")
            File(repoDir, ".git/config").writeText("[remote \"origin\"]\n\turl = git@github.com:tonimadev/Kommander.git\n")

            val port = freePort()
            val repository = WebhookActivityRepository(WebhookServerConfig(port = port))
            repository.start()
            try {
                val next = async { withTimeout(5_000) { repository.activities.first() } }
                val body = """
                    {"session_id":"abc","cwd":"${repoDir.path}","hook_event_name":"PreToolUse",
                     "tool_name":"Bash","tool_input":{"command":"gh pr create --fill"}}
                """.trimIndent()
                withContext(Dispatchers.IO) {
                    HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/hooks/claude-code"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                        HttpResponse.BodyHandlers.discarding(),
                    )
                }

                val activity = next.await()
                assertEquals(ActivityStatus.OPENING_PR, activity.status)
                assertEquals("tonimadev/Kommander", activity.repository?.slug)
                assertEquals("main", activity.repository?.branch)
                assertEquals("abc", activity.sessionId)
            } finally {
                repository.stop()
            }
        } finally {
            repoDir.deleteRecursively()
        }
    }

    @Test
    fun `port already in use results in Failed state`() = runBlocking {
        ServerSocket(0).use { occupied ->
            val repository = WebhookActivityRepository(WebhookServerConfig(port = occupied.localPort))
            repository.start()
            assertIs<ListenerState.Failed>(repository.listenerState.value)
            repository.stop()
        }
    }
}
