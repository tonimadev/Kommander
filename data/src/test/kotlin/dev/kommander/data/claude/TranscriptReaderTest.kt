package dev.kommander.data.claude

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranscriptReaderTest {

    private val dir = Files.createTempDirectory("kommander-transcript").toFile()

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun transcript(vararg lines: String): File =
        File(dir, "session.jsonl").apply { writeText(lines.joinToString("\n", postfix = "\n")) }

    private fun prompt(text: String) = """{"type":"user","message":{"role":"user","content":"$text"}}"""
    private fun toolResult() = """{"type":"user","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"t","content":"ok"}]}}"""
    private fun meta() = """{"type":"user","isMeta":true,"message":{"role":"user","content":"<system-reminder>x</system-reminder>"}}"""
    private fun text(text: String) = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"$text"}]}}"""
    private fun toolUse() = """{"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"t","name":"Read","input":{}}]}}"""
    private fun attachment() = """{"type":"attachment","attachment":{}}"""

    @Test
    fun `finds the last assistant text, skipping tool calls and results`() {
        val file = transcript(
            prompt("corrija o bug"),
            text("Primeiro vou entender o reducer."),
            toolUse(),
            toolResult(),
            text("Achei: o filtro não é resetado."),
            toolUse(),
            toolResult(),
            attachment(),
        )
        assertEquals("Achei: o filtro não é resetado.", FileTranscriptReader().lastAssistantText(file.path))
    }

    @Test
    fun `text from a previous prompt does not describe the current one`() {
        val file = transcript(text("Pronto, tudo feito."), prompt("agora rode os testes"), toolUse(), toolResult())
        assertNull(FileTranscriptReader().lastAssistantText(file.path))
    }

    @Test
    fun `system reminders are not mistaken for your prompt`() {
        val file = transcript(prompt("x"), text("Vou carregar a skill."), toolUse(), meta(), toolResult())
        assertEquals("Vou carregar a skill.", FileTranscriptReader().lastAssistantText(file.path))
    }

    @Test
    fun `reads only the tail of large transcripts`() {
        val filler = List(2_000) { toolResult() }.toTypedArray()
        val file = transcript(prompt("x"), text("antigo"), *filler, text("recente"))
        assertEquals("recente", FileTranscriptReader(tailBytes = 4_096).lastAssistantText(file.path))
    }

    @Test
    fun `ignores missing or non-jsonl paths`() {
        assertNull(FileTranscriptReader().lastAssistantText(File(dir, "nope.jsonl").path))
        val other = File(dir, "notes.txt").apply { writeText(text("oi")) }
        assertNull(FileTranscriptReader().lastAssistantText(other.path))
    }

    @Test
    fun `narration keeps the first paragraph without markdown`() {
        val raw = "## Plano\n- **Ler** o `Reducer` e [docs](http://x)\n\nSegundo parágrafo."
        assertEquals("Plano Ler o Reducer e docs", raw.toNarration())
        assertNull("   ".toNarration())
        assertEquals(220, "a".repeat(500).toNarration()!!.length)
    }
}
