package dev.kommander.data.remote

import dev.kommander.data.claude.ClaudeHookDto
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

/** Recebe os payloads já desserializados. Devolve o id atribuído, ou `null` se o evento foi ignorado. */
interface EventIngestor {
    suspend fun onEvent(event: ActivityEventDto): Long
    suspend fun onClaudeHook(hook: ClaudeHookDto): Long?
}

/**
 * Módulo Ktor com as rotas. Isolado do bootstrap do servidor para ser testável com
 * `testApplication { application { eventsModule(...) } }`.
 *
 * - `POST /events`             payload genérico (seu script de automação)
 * - `POST /hooks/claude-code`  JSON cru dos hooks do Claude Code
 * - `GET  /health`
 */
fun Application.eventsModule(ingestor: EventIngestor, eventsPath: String = "/events") {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            },
        )
    }
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.rootMessage()))
        }
    }

    routing {
        post(eventsPath) {
            val dto = call.receive<ActivityEventDto>()
            if (dto.status.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("'status' must not be blank"))
                return@post
            }
            val id = ingestor.onEvent(dto)
            call.respond(HttpStatusCode.Accepted, EventAcceptedResponse(accepted = true, id = id))
        }
        post("/hooks/claude-code") {
            val hook = call.receive<ClaudeHookDto>()
            val id = ingestor.onClaudeHook(hook)
            call.respond(HttpStatusCode.Accepted, EventAcceptedResponse(accepted = id != null, id = id))
        }
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
    }
}

private fun Throwable.rootMessage(): String {
    var current: Throwable = this
    while (current.cause != null && current.cause !== current) current = current.cause!!
    return current.message ?: "Invalid request"
}
