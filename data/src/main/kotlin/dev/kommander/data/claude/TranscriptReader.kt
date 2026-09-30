package dev.kommander.data.claude

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.RandomAccessFile

/** Descobre o que o Claude disse por último na conversa (o "pensamento em voz alta" entre ferramentas). */
fun interface TranscriptReader {
    fun lastAssistantText(transcriptPath: String): String?
}

/**
 * Lê o final do transcript JSONL da sessão (`transcript_path` dos hooks), de trás para
 * frente, até achar um bloco de texto do assistente. Para no último prompt do usuário:
 * texto de um pedido anterior não descreve o trabalho atual.
 *
 * Só lê os últimos [tailBytes] (o arquivo cresce a cada ferramenta) e guarda o resultado
 * por tamanho de arquivo, então hooks seguidos sem texto novo não releem nada.
 */
class FileTranscriptReader(private val tailBytes: Int = 256 * 1024) : TranscriptReader {

    private val json = Json { ignoreUnknownKeys = true }
    private var cache: Triple<String, Long, String?>? = null

    @Synchronized
    override fun lastAssistantText(transcriptPath: String): String? {
        val file = File(transcriptPath)
        if (!transcriptPath.endsWith(".jsonl") || !file.isFile) return null
        val size = file.length()
        cache?.let { (path, cachedSize, text) -> if (path == transcriptPath && cachedSize == size) return text }

        val text = runCatching { scan(readTail(file, size)) }.getOrNull()
        cache = Triple(transcriptPath, size, text)
        return text
    }

    private fun readTail(file: File, size: Long): List<String> {
        val start = (size - tailBytes).coerceAtLeast(0)
        val bytes = ByteArray((size - start).toInt())
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            raf.readFully(bytes)
        }
        val lines = String(bytes, Charsets.UTF_8).split('\n')
        // Se começamos no meio do arquivo, a primeira linha está cortada.
        return if (start > 0) lines.drop(1) else lines
    }

    private fun scan(lines: List<String>): String? {
        for (line in lines.asReversed()) {
            if (line.isBlank()) continue
            // Filtro barato antes de desserializar: a maioria das linhas é tool_result/attachment.
            val isAssistant = line.contains("\"type\":\"assistant\"")
            val isUser = line.contains("\"type\":\"user\"")
            if (!isAssistant && !isUser) continue
            val entry = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: continue
            // Mensagens "meta" (lembretes do sistema, skills carregadas) não são prompts seus.
            if ((entry["isMeta"] as? JsonPrimitive)?.content == "true") continue
            val type = (entry["type"] as? JsonPrimitive)?.content
            val content = (entry["message"] as? JsonObject)?.get("content")
            when (type) {
                "user" -> if (content.isUserPrompt()) return null
                "assistant" -> content.lastText()?.let { return it }
            }
        }
        return null
    }

    /** Prompt digitado (string ou blocos de texto), ao contrário de um `tool_result`. */
    private fun JsonElement?.isUserPrompt(): Boolean = when (this) {
        is JsonPrimitive -> isString
        is JsonArray -> any { (it as? JsonObject)?.get("type")?.let { t -> (t as? JsonPrimitive)?.content } == "text" } &&
            none { (it as? JsonObject)?.get("type")?.let { t -> (t as? JsonPrimitive)?.content } == "tool_result" }
        else -> false
    }

    private fun JsonElement?.lastText(): String? =
        (this as? JsonArray)
            ?.mapNotNull { block ->
                (block as? JsonObject)
                    ?.takeIf { (it["type"] as? JsonPrimitive)?.content == "text" }
                    ?.let { (it["text"] as? JsonPrimitive)?.content }
            }
            ?.lastOrNull { it.isNotBlank() }
}

private const val MAX_NARRATION = 220

/**
 * Deixa um texto em markdown apresentável numa linha do card: sem ênfase, código, títulos
 * ou marcadores de lista; só o primeiro parágrafo, limitado a [MAX_NARRATION] caracteres.
 */
internal fun String.toNarration(): String? {
    val paragraph = trim().split(Regex("""\n\s*\n""")).firstOrNull { it.isNotBlank() } ?: return null
    val flat = paragraph.lineSequence()
        .map { it.trim().replace(Regex("""^(#+|[-*+]|\d+\.)\s+"""), "") }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .replace(Regex("""\*\*|__|`"""), "")
        .replace(Regex("""\[([^]]+)]\([^)]+\)"""), "$1")
    if (flat.isBlank()) return null
    return if (flat.length > MAX_NARRATION) flat.take(MAX_NARRATION - 1).trimEnd() + "…" else flat
}
