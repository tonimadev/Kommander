package dev.kommander.data.remote

import dev.kommander.data.claude.ClaudeHookDto
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventsModuleTest {

    private class RecordingIngestor : EventIngestor {
        val events = mutableListOf<ActivityEventDto>()
        val hooks = mutableListOf<ClaudeHookDto>()
        override suspend fun onEvent(event: ActivityEventDto): Long { events += event; return 42 }
        override suspend fun onClaudeHook(hook: ClaudeHookDto): Long? {
            hooks += hook
            return if (hook.hookEventName == "PostToolUse") null else 7
        }
    }

    @Test
    fun `POST events accepts a valid payload and forwards it`() = testApplication {
        val ingestor = RecordingIngestor()
        application { eventsModule(ingestor) }

        val response = client.post("/events") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {"timestamp":"2026-09-30T12:00:00Z","agent":"claude-code","status":"DEPLOYING",
                 "target":"Magalu Cloud","message":"Subindo os containers...",
                 "repository":"tonimadev/Kommander","session_id":"s1","extra":"ignored"}
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertTrue(response.bodyAsText().contains("\"id\":42"))
        with(ingestor.events.single()) {
            assertEquals("DEPLOYING", status)
            assertEquals("tonimadev/Kommander", repository)
            assertEquals("s1", sessionId)
        }
    }

    @Test
    fun `POST hooks accepts raw Claude Code hook json`() = testApplication {
        val ingestor = RecordingIngestor()
        application { eventsModule(ingestor) }

        val response = client.post("/hooks/claude-code") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {"session_id":"abc","transcript_path":"/tmp/t.jsonl","cwd":"/home/me/proj",
                 "hook_event_name":"PreToolUse","tool_name":"Edit",
                 "tool_input":{"file_path":"/home/me/proj/src/Main.kt","old_string":"a","new_string":"b"}}
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        with(ingestor.hooks.single()) {
            assertEquals("Edit", toolName)
            assertEquals("/home/me/proj", cwd)
        }
    }

    @Test
    fun `ignored hook answers accepted false`() = testApplication {
        application { eventsModule(RecordingIngestor()) }
        val response = client.post("/hooks/claude-code") {
            contentType(ContentType.Application.Json)
            setBody("""{"hook_event_name":"PostToolUse","session_id":"abc"}""")
        }
        assertEquals(HttpStatusCode.Accepted, response.status)
        assertTrue(response.bodyAsText().contains("\"accepted\":false"))
    }

    @Test
    fun `POST events rejects payload without status`() = testApplication {
        application { eventsModule(RecordingIngestor()) }

        val response = client.post("/events") {
            contentType(ContentType.Application.Json)
            setBody("""{"agent":"claude-code"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST events rejects malformed json`() = testApplication {
        application { eventsModule(RecordingIngestor()) }

        val response = client.post("/events") {
            contentType(ContentType.Application.Json)
            setBody("{not json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET health answers ok`() = testApplication {
        application { eventsModule(RecordingIngestor()) }
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }
}
