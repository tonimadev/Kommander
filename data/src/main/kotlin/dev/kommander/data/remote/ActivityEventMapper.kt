package dev.kommander.data.remote

import dev.kommander.domain.model.ActivityStatus
import dev.kommander.domain.model.AgentActivity
import dev.kommander.domain.model.RepositoryRef
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

internal const val DEFAULT_AGENT = "unknown-agent"

/**
 * @param resolvedFromCwd repositório descoberto a partir de [ActivityEventDto.cwd], se houver.
 *   Campos explícitos do payload têm precedência sobre ele.
 */
internal fun ActivityEventDto.toDomain(
    id: Long,
    resolvedFromCwd: RepositoryRef? = null,
    now: () -> Instant = Instant::now,
): AgentActivity {
    val explicitRepo = repository?.takeIf { it.isNotBlank() }
    val repositoryRef = when {
        explicitRepo != null -> RepositoryRef(
            slug = explicitRepo,
            branch = branch?.takeIf { it.isNotBlank() } ?: resolvedFromCwd?.branch,
            localPath = cwd ?: resolvedFromCwd?.localPath,
        )
        resolvedFromCwd != null -> resolvedFromCwd.copy(branch = branch?.takeIf { it.isNotBlank() } ?: resolvedFromCwd.branch)
        else -> null
    }
    return AgentActivity(
        id = id,
        timestamp = parseTimestamp(timestamp) ?: now(),
        agent = agent?.takeIf { it.isNotBlank() } ?: DEFAULT_AGENT,
        sessionId = sessionId?.takeIf { it.isNotBlank() },
        repository = repositoryRef,
        status = ActivityStatus.parse(status),
        target = target?.takeIf { it.isNotBlank() },
        message = message?.takeIf { it.isNotBlank() },
        tool = tool?.takeIf { it.isNotBlank() },
    )
}

/** Aceita ISO-8601 com `Z` ou com offset (`-03:00`) e epoch em milissegundos. */
internal fun parseTimestamp(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    raw.toLongOrNull()?.let { return Instant.ofEpochMilli(it) }
    return try {
        Instant.parse(raw)
    } catch (_: DateTimeParseException) {
        try {
            OffsetDateTime.parse(raw).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
