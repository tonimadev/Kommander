package dev.kommander.data.claude

import dev.kommander.data.claude.ClaudeHookClassifier.oneLine
import dev.kommander.domain.model.ActivityStatus

/**
 * Heurística para descobrir a intenção de um comando `Bash` executado pelo Claude.
 * A ordem das regras importa: deploy > GitHub > testes > build > genérico.
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
                """\b(jest|vitest|playwright\s+test)\b""",
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
