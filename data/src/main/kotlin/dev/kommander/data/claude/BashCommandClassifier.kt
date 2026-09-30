package dev.kommander.data.claude

import dev.kommander.data.claude.ClaudeHookClassifier.oneLine
import dev.kommander.domain.model.ActivityStatus

/**
 * Heurística para descobrir a intenção de um comando `Bash` executado pelo Claude.
 * A ordem das regras importa: deploy > GitHub > testes > lint > dependências > build >
 * dispositivo > git > HTTP > leitura de arquivos > genérico.
 */
internal object BashCommandClassifier {

    private data class Rule(
        val status: ActivityStatus,
        val message: String,
        val target: String? = null,
        val pattern: Regex,
    )

    private fun rx(vararg alternatives: String) =
        Regex(alternatives.joinToString("|") { "(?:$it)" }, RegexOption.IGNORE_CASE)

    /** Início de comando: começo da linha ou depois de `;`, `&&`, `|`, `(`. */
    private const val CMD = """(?:^|[;&|(]\s*)\s*"""

    private val rules = listOf(
        Rule(ActivityStatus.DEPLOYING, "Deploy na Magalu Cloud", "Magalu Cloud", rx("""(^|[\s;&|])mgc\s""")),
        Rule(
            ActivityStatus.DEPLOYING, "Fazendo deploy",
            pattern = rx(
                """\bkubectl\s+(apply|rollout|set|scale)""", """\bhelm\s+(install|upgrade)""",
                """\bterraform\s+apply""", """\bdocker\s+push""", """\bdocker[\s-]compose\s+up""",
                """\bdeploy\b""", """\bfly\s+deploy""", """\bvercel\b""",
            ),
        ),
        Rule(ActivityStatus.OPENING_PR, "Abrindo pull request", pattern = rx("""\bgh\s+pr\s+create""")),
        Rule(ActivityStatus.OPENING_ISSUE, "Abrindo issue", pattern = rx("""\bgh\s+issue\s+create""")),
        Rule(ActivityStatus.REVIEWING, "Revisando no GitHub", pattern = rx("""\bgh\s+pr\s+(review|checks|view|diff)""")),
        Rule(ActivityStatus.COMMITTING, "Enviando para o GitHub", pattern = rx("""\bgit\s+push\b""")),
        Rule(ActivityStatus.COMMITTING, "Criando commit", pattern = rx("""\bgit\s+commit\b""")),
        Rule(ActivityStatus.GITHUB, "Operando no GitHub", pattern = rx("""\bgh\s+\w+""")),
        Rule(
            ActivityStatus.TESTING, "Rodando testes",
            pattern = rx(
                """gradlew?\s+.*\b(test|check|\w*Test)\b""", """\b(npm|pnpm|yarn|bun)\s+(run\s+)?test""",
                """\bpytest\b""", """\bgo\s+test\b""", """\bcargo\s+test\b""", """\bmvn\s+.*\b(test|verify)\b""",
                """\b(jest|vitest|playwright\s+test)\b""", """\badb\b.*\bam\s+instrument\b""",
            ),
        ),
        Rule(
            ActivityStatus.TESTING, "Verificando o código (lint)",
            pattern = rx(
                """gradlew?\s+.*\b(lint\w*|detekt|ktlint\w*|spotless\w*)\b""", """\b(ktlint|detekt|eslint|prettier|ruff|flake8|mypy|pylint|clippy|golangci-lint|shellcheck)\b""",
                """\b(npm|pnpm|yarn|bun)\s+(run\s+)?(lint|typecheck)""", """\btsc\b""",
            ),
        ),
        Rule(
            ActivityStatus.BUILDING, "Instalando dependências",
            pattern = rx(
                """\b(npm|pnpm|bun)\s+(install|i|ci|add)\b""", """\byarn(\s+(install|add)\b|\s*$)""", """\bpip3?\s+install\b""",
                """\buv\s+(sync|add|pip\s+install)\b""", """\bpoetry\s+(install|add)\b""", """\bcargo\s+add\b""",
                """\bgo\s+(get|mod\s+(tidy|download))\b""", """\b(apt(-get)?|dnf|pacman|brew)\s+(install|-S)\b""",
            ),
        ),
        Rule(
            ActivityStatus.BUILDING, "Compilando",
            pattern = rx(
                """gradlew?\s+.*\b(build|assemble|compile\w*|package\w*)\b""", """\b(npm|pnpm|yarn|bun)\s+(run\s+)?build""",
                """\bcargo\s+build\b""", """\bgo\s+build\b""", """\bdocker\s+build\b""", """\bmvn\s+.*\bpackage\b""",
                """\bmake\b""",
            ),
        ),
        Rule(ActivityStatus.DEPLOYING, "Instalando no dispositivo", pattern = rx("""\badb\b.*\binstall\b""", """gradlew?\s+.*\binstall\w+""")),
        Rule(ActivityStatus.EXPLORING, "Inspecionando o dispositivo", pattern = rx("""\badb\b""")),
        Rule(
            ActivityStatus.RUNNING_COMMAND, "Executando a aplicação",
            pattern = rx(
                """gradlew?\s+.*\brun\b""", """\b(npm|pnpm|yarn|bun)\s+(run\s+)?(dev|start|serve)\b""",
                """\bcargo\s+run\b""", """\bgo\s+run\b""", """\bdocker(\s+compose)?\s+run\b""",
            ),
        ),
        Rule(
            ActivityStatus.EXPLORING, "Consultando o git",
            pattern = rx("""\bgit\s+(status|diff|log|show|blame|branch|remote|fetch|ls-files|rev-parse|describe|shortlog)\b"""),
        ),
        Rule(
            ActivityStatus.GITHUB, "Mexendo no histórico do git",
            pattern = rx("""\bgit\s+(checkout|switch|merge|rebase|pull|reset|stash|cherry-pick|tag|add|restore|clone|filter-branch|filter-repo)\b"""),
        ),
        Rule(ActivityStatus.EXPLORING, "Fazendo uma requisição HTTP", pattern = rx("""\b(curl|wget|http|https|xh)\s""")),
        Rule(
            ActivityStatus.EXPLORING, "Lendo arquivos",
            pattern = rx("""$CMD(ls|cat|bat|head|tail|less|grep|egrep|rg|ag|find|fd|tree|wc|sed\s+-n|awk|jq|yq|file|stat|du|strings|diff|which|readlink)\b"""),
        ),
        Rule(ActivityStatus.CODING, "Mexendo em arquivos", pattern = rx("""$CMD(mkdir|cp|mv|rm|touch|chmod|ln|sed\s+-i|patch|tee)\b""")),
    )

    fun classify(command: String, description: String?): HookClassification {
        val shown = description?.oneLine() ?: command.oneLine()
        val rule = rules.firstOrNull { it.pattern.containsMatchIn(command) }
            ?: return HookClassification(ActivityStatus.RUNNING_COMMAND, target = command.oneLine(), message = description?.oneLine() ?: "Executando comando")
        // Regras com alvo fixo (ex.: "Magalu Cloud") mostram o comando como mensagem.
        return if (rule.target != null) {
            HookClassification(status = rule.status, target = rule.target, message = shown)
        } else {
            HookClassification(status = rule.status, target = shown, message = rule.message)
        }
    }
}
