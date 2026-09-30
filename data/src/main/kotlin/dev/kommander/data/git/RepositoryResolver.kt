package dev.kommander.data.git

import dev.kommander.domain.model.RepositoryRef
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Descobre em qual repositório git um diretório de trabalho (`cwd`) está. */
fun interface RepositoryResolver {
    fun resolve(cwd: String): RepositoryRef?
}

/**
 * Lê `.git/config` e `.git/HEAD` diretamente (sem subprocessos `git`), subindo a
 * árvore de diretórios a partir do `cwd`. Suporta worktrees (`.git` como arquivo).
 *
 * O slug do remote é cacheado por raiz de repositório; a branch é relida a cada
 * chamada (é um arquivo minúsculo e muda com `git checkout`).
 */
class FileSystemRepositoryResolver : RepositoryResolver {

    private val slugCache = ConcurrentHashMap<String, String>()

    override fun resolve(cwd: String): RepositoryRef? {
        val root = findRepoRoot(File(cwd)) ?: return null
        val gitDir = gitDirOf(root) ?: return null
        val commonDir = File(gitDir, "commondir").takeIf { it.isFile }
            ?.let { File(gitDir, it.readText().trim()).canonicalFile }
            ?: gitDir

        val slug = slugCache.getOrPut(root.path) {
            readOriginUrl(File(commonDir, "config"))?.let(::slugFromRemoteUrl) ?: root.name
        }
        return RepositoryRef(slug = slug, branch = readBranch(File(gitDir, "HEAD")), localPath = root.path)
    }

    private fun findRepoRoot(start: File): File? {
        var dir: File? = start.absoluteFile
        while (dir != null) {
            if (File(dir, ".git").exists()) return dir
            dir = dir.parentFile
        }
        return null
    }

    private fun gitDirOf(root: File): File? {
        val dotGit = File(root, ".git")
        if (dotGit.isDirectory) return dotGit
        // Worktree/submódulo: arquivo com "gitdir: <caminho>"
        val pointer = runCatching { dotGit.readText().trim() }.getOrNull() ?: return null
        if (!pointer.startsWith("gitdir:")) return null
        val path = pointer.removePrefix("gitdir:").trim()
        return (File(path).takeIf { it.isAbsolute } ?: File(root, path)).canonicalFile
    }

    private fun readOriginUrl(config: File): String? {
        if (!config.isFile) return null
        var inOrigin = false
        var firstUrl: String? = null
        for (raw in config.readLines()) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                inOrigin = line == "[remote \"origin\"]"
            } else if (line.startsWith("url") && line.contains('=')) {
                val url = line.substringAfter('=').trim()
                if (inOrigin) return url
                if (firstUrl == null) firstUrl = url
            }
        }
        return firstUrl
    }

    private fun readBranch(head: File): String? {
        val content = runCatching { head.readText().trim() }.getOrNull() ?: return null
        return if (content.startsWith("ref:")) {
            content.removePrefix("ref:").trim().removePrefix("refs/heads/")
        } else {
            content.take(7) // HEAD destacado: mostra o hash curto
        }
    }

    companion object {
        /**
         * `git@github.com:owner/repo.git`, `https://github.com/owner/repo`,
         * `ssh://git@host:22/owner/repo.git`, `http://proxy@127.0.0.1:1234/git/owner/repo` -> `owner/repo`
         */
        fun slugFromRemoteUrl(url: String): String? {
            val segments = url.trim()
                .removeSuffix("/")
                .removeSuffix(".git")
                .split('/', ':')
                .filter { it.isNotBlank() }
            if (segments.size < 2) return segments.lastOrNull()
            return "${segments[segments.size - 2]}/${segments.last()}"
        }
    }
}
