package dev.kommander.domain.model

import java.time.Instant

/**
 * Uma atividade reportada por um agente (ex.: uma sessão do Claude Code).
 *
 * É a entidade central do domínio: independe de como chegou (hook do Claude Code,
 * webhook do seu script, arquivo...).
 */
data class AgentActivity(
    val id: Long,
    val timestamp: Instant,
    val agent: String,
    /** Sessão do agente. Várias sessões podem rodar ao mesmo tempo em repositórios diferentes. */
    val sessionId: String?,
    val repository: RepositoryRef?,
    val status: ActivityStatus,
    /** Sobre o que o agente está agindo: arquivo, comando, PR, "Magalu Cloud"... */
    val target: String?,
    val message: String?,
    /** Ferramenta usada pelo agente (Edit, Bash, mcp__github__create_pull_request...). */
    val tool: String? = null,
) {
    /** Chave que agrupa atividades de uma mesma sessão de trabalho. */
    val sessionKey: String
        get() = sessionId ?: repository?.slug ?: agent
}

/**
 * Repositório onde o agente está trabalhando.
 *
 * @param slug `owner/repo` quando há remote configurado, ou o nome da pasta.
 */
data class RepositoryRef(
    val slug: String,
    val branch: String? = null,
    val localPath: String? = null,
) {
    val name: String get() = slug.substringAfterLast('/')
    val owner: String? get() = slug.substringBeforeLast('/', missingDelimiterValue = "").ifEmpty { null }
}
