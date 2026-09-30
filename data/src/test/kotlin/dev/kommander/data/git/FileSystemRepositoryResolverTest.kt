package dev.kommander.data.git

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileSystemRepositoryResolverTest {

    private val tmp: File = Files.createTempDirectory("kommander-git").toFile().canonicalFile

    @AfterTest
    fun cleanup() {
        tmp.deleteRecursively()
    }

    @Test
    fun `resolves slug and branch from a nested cwd`() {
        val repo = File(tmp, "Kommander").apply { mkdirs() }
        File(repo, ".git").mkdirs()
        File(repo, ".git/HEAD").writeText("ref: refs/heads/feature/dashboard\n")
        File(repo, ".git/config").writeText(
            """
            [core]
            	bare = false
            [remote "upstream"]
            	url = https://github.com/someone/else.git
            [remote "origin"]
            	url = git@github.com:tonimadev/Kommander.git
            """.trimIndent(),
        )
        val nested = File(repo, "desktopApp/src").apply { mkdirs() }

        val ref = FileSystemRepositoryResolver().resolve(nested.path)!!

        assertEquals("tonimadev/Kommander", ref.slug)
        assertEquals("feature/dashboard", ref.branch)
        assertEquals(repo.canonicalFile.path, File(ref.localPath!!).canonicalFile.path)
    }

    @Test
    fun `falls back to folder name without remote`() {
        val repo = File(tmp, "scratch").apply { mkdirs() }
        File(repo, ".git").mkdirs()
        File(repo, ".git/HEAD").writeText("ref: refs/heads/main")
        assertEquals("scratch", FileSystemRepositoryResolver().resolve(repo.path)?.slug)
    }

    @Test
    fun `returns null outside a repository`() {
        assertNull(FileSystemRepositoryResolver().resolve(tmp.path))
    }

    @Test
    fun `parses remote url formats`() {
        val cases = mapOf(
            "git@github.com:tonimadev/Kommander.git" to "tonimadev/Kommander",
            "https://github.com/tonimadev/Kommander" to "tonimadev/Kommander",
            "ssh://git@gitlab.com:22/group/app.git" to "group/app",
            "http://proxy@127.0.0.1:1234/git/tonimadev/Kommander" to "tonimadev/Kommander",
        )
        cases.forEach { (url, slug) -> assertEquals(slug, FileSystemRepositoryResolver.slugFromRemoteUrl(url), url) }
    }
}
