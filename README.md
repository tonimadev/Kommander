# Kommander

Dashboard *companion* em **Compose Desktop** que mostra, em tempo real, **o que o Claude Code está fazendo e em qual repositório**: lendo código, editando, rodando testes, abrindo PR no GitHub, fazendo deploy na Magalu Cloud, esperando sua resposta…

```
Claude Code ──hook (stdin JSON)──► curl ──POST──► Ktor (127.0.0.1:8080) ──SharedFlow──► ViewModel (StateFlow) ──► Compose UI
Seu script  ──────────────────────────────POST /events──┘
```

- Um **card por sessão** (estilo Google Now) com repositório, branch, status atual, ícone/cor por categoria e `LinearProgressIndicator` indeterminado enquanto há trabalho em andamento.
- Sessão que **precisa de você** (permissão, pergunta) ganha destaque rosa.
- **Linha do tempo** de tudo o que aconteceu, com filtro por repositório.
- Botão de **fixar janela no topo** para deixar o companion sempre visível.

## Rodando

Requisitos: JDK 17+.

```bash
git clone https://github.com/tonimadev/Kommander.git ~/IntelliJIdeaProjects/Kommander
cd ~/IntelliJIdeaProjects/Kommander
git checkout claude/companion-dashboard-compose-1nshmf

./gradlew :desktopApp:run                       # porta padrão 8080
./gradlew :desktopApp:run --args="--port=9090"  # ou KOMMANDER_PORT=9090
```

Em outro terminal, veja o dashboard reagir a duas sessões simuladas:

```bash
./scripts/simulate.sh
```

Pacote nativo para Linux: `./gradlew :desktopApp:packageDeb` (ou `packageRpm`, `packageAppImage`).

## Conectando ao Claude Code

O Claude Code executa [hooks](https://docs.claude.com/en/docs/claude-code/hooks) em cada etapa e passa um JSON no stdin
(`session_id`, `cwd`, `tool_name`, `tool_input`…). Cada hook só repassa esse JSON com `curl` para
`POST /hooks/claude-code`; o Kommander interpreta o evento e descobre o repositório lendo o `.git` do `cwd`.

```bash
./integrations/claude-code/install-hooks.sh   # mescla em ~/.claude/settings.json (com backup, requer jq)
```

Ou copie o conteúdo de [`integrations/claude-code/hooks.json`](integrations/claude-code/hooks.json) para o
`settings.json` (de usuário ou de um projeto). Os hooks usam `--max-time 1` e `|| true`: se o Kommander
estiver fechado, o Claude Code segue normalmente. A saída do `curl` vai para `/dev/null` para não ser
injetada no contexto do Claude (no `UserPromptSubmit`/`SessionStart`, o stdout do hook vira contexto).

| Hook do Claude Code                                   | Status no dashboard          |
|-------------------------------------------------------|------------------------------|
| `SessionStart`                                        | Sessão iniciada              |
| `UserPromptSubmit`                                    | Pensando (mostra o prompt)   |
| `PreToolUse` Read / Grep / Glob / WebFetch            | Explorando                   |
| `PreToolUse` Edit / Write / MultiEdit                 | Escrevendo código (arquivo)  |
| `PreToolUse` Task / TodoWrite                         | Planejando                   |
| `PreToolUse` Bash `gradle test`, `npm test`, `pytest`…| Testando                     |
| `PreToolUse` Bash `gradle build`, `docker build`…     | Build                        |
| `PreToolUse` Bash `git commit` / `git push`           | Commit / push                |
| `PreToolUse` Bash `gh pr create`, `mcp__github__*`    | GitHub (PR, issue, review…)  |
| `PreToolUse` Bash `mgc …`                             | Deploy → **Magalu Cloud**    |
| `PreToolUse` Bash `kubectl apply`, `helm`, `terraform apply`… | Deploy               |
| `Notification`                                        | Aguardando você              |
| `Stop`                                                | Concluído                    |
| `SessionEnd`                                          | Sessão encerrada             |

> **Sessões na nuvem (claude.ai/code):** os hooks rodam no container remoto, onde `127.0.0.1` não é a sua
> máquina. Para elas, exponha o Kommander com um túnel (ex.: `cloudflared`, `ngrok`) e defina
> `KOMMANDER_URL` no ambiente da sessão — e só faça isso com autenticação no túnel, pois o endpoint não tem auth.

## Webhook genérico (`POST /events`)

Para o seu script de automação (ou qualquer outro agente):

```bash
curl -X POST localhost:8080/events -H 'Content-Type: application/json' -d '{
  "timestamp": "2026-09-30T12:00:00Z",
  "agent": "claude-code",
  "status": "DEPLOYING",
  "target": "Magalu Cloud",
  "message": "Subindo os containers...",
  "repository": "tonimadev/api-pagamentos",
  "branch": "main",
  "session_id": "deploy-42"
}'
```

Só `status` é obrigatório. `repository`/`branch` podem ser omitidos se você mandar `cwd` (o repo é resolvido pelo `.git`).
`timestamp` aceita ISO-8601 (com `Z` ou offset) ou epoch em ms. Status aceitos (case-insensitive):
`THINKING, PLANNING, EXPLORING, CODING, RUNNING_COMMAND, TESTING, GITHUB, COMMITTING, OPENING_ISSUE, OPENING_PR,
REVIEWING, BUILDING, DEPLOYING, WAITING_INPUT, DONE, SUCCESS, FAILED, ERROR, SESSION_STARTED, SESSION_ENDED, IDLE`.

Respostas: `202 {"accepted":true,"id":N}`, `400` para JSON inválido/sem `status`. `GET /health` → `200`.

## Arquitetura

Clean Architecture em 4 módulos Gradle — a regra de dependência é garantida pelo compilador:

```
desktopApp ──► presentation ──► domain ◄── data
     └───────────────(só no AppContainer)──────┘
```

| Módulo         | Conteúdo                                                                                           | Depende de                     |
|----------------|----------------------------------------------------------------------------------------------------|--------------------------------|
| `domain`       | `AgentActivity`, `RepositoryRef`, `ActivityStatus`/`ActivityCategory`, porta `ActivityRepository`, use cases | coroutines                     |
| `data`         | Ktor Embedded Server (CIO), DTOs, classificador dos hooks do Claude, resolvedor de repositório git, `WebhookActivityRepository` (`MutableSharedFlow` + `MutableStateFlow`) | domain, Ktor                   |
| `presentation` | MVI: `DashboardState`, `DashboardIntent`, `DashboardReducer` (função pura), `DashboardViewModel` (`StateFlow`) | domain (Kotlin puro, sem Compose) |
| `desktopApp`   | `main`, composition root (`di/AppContainer`), tema M3 e `@Composable`s                              | presentation, data (só DI)     |

Fluxo unidirecional: **UI → `DashboardIntent` → ViewModel → `DashboardMutation` → `DashboardReducer` → `StateFlow<DashboardState>` → UI**.
Eventos da camada de dados entram no mesmo reducer como mutações.

O servidor escuta em `127.0.0.1` por padrão (só processos locais). Use `--host=0.0.0.0` apenas se souber o que está fazendo.

## Testes

```bash
./gradlew test
```

Cobrem: rotas Ktor (`testApplication`), servidor real via HTTP (incluindo porta ocupada), classificação dos hooks
e comandos Bash, resolução de repositório/branch/worktree a partir do `.git`, reducer e ViewModel.
