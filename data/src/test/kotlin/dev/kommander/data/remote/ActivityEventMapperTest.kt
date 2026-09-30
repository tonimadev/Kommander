package dev.kommander.data.remote

import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.RepositoryRef
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityEventMapperTest {

    private val fixedNow = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `maps full payload`() {
        val activity = ActivityEventDto(
            timestamp = "2026-09-30T09:00:00-03:00",
            agent = "claude-code",
            status = "deploying",
            target = "Magalu Cloud",
            message = "Subindo os containers...",
            repository = "tonimadev/Kommander",
            branch = "main",
        ).toDomain(id = 1, now = { fixedNow })

        assertEquals(Instant.parse("2026-09-30T12:00:00Z"), activity.timestamp)
        assertEquals(ActivityStatus.DEPLOYING, activity.status)
        assertEquals("Magalu Cloud", activity.target)
        assertEquals(RepositoryRef("tonimadev/Kommander", "main"), activity.repository)
    }

    @Test
    fun `uses repository resolved from cwd when payload has none`() {
        val resolved = RepositoryRef("acme/api", "feature/x", "/src/api")
        val activity = ActivityEventDto(status = "CODING", cwd = "/src/api/app")
            .toDomain(id = 1, resolvedFromCwd = resolved, now = { fixedNow })
        assertEquals(resolved, activity.repository)
    }

    @Test
    fun `falls back to defaults on missing or invalid fields`() {
        val activity = ActivityEventDto(timestamp = "yesterday", agent = " ", status = "DANCING", target = "")
            .toDomain(id = 7, now = { fixedNow })

        assertEquals(fixedNow, activity.timestamp)
        assertEquals(DEFAULT_AGENT, activity.agent)
        assertEquals(ActivityStatus.UNKNOWN, activity.status)
        assertNull(activity.target)
        assertNull(activity.repository)
    }

    @Test
    fun `accepts epoch millis`() {
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000), parseTimestamp("1700000000000"))
    }
}
