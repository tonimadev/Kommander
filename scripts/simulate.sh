#!/usr/bin/env bash
# Simula duas sessões do Claude Code trabalhando em repositórios diferentes,
# além de um evento do seu script de automação via /events.
set -euo pipefail
BASE="${KOMMANDER_URL:-http://127.0.0.1:8080}"
DELAY="${DELAY:-1.5}"

hook() { curl -s -o /dev/null -w "%{http_code} " -H 'Content-Type: application/json' -d "$1" "$BASE/hooks/claude-code"; echo "$2"; sleep "$DELAY"; }
event() { curl -s -o /dev/null -w "%{http_code} " -H 'Content-Type: application/json' -d "$1" "$BASE/events"; echo "$2"; sleep "$DELAY"; }

hook '{"hook_event_name":"SessionStart","session_id":"demo-a","cwd":"'"$PWD"'","source":"startup"}' "A: sessão iniciada (repo atual)"
hook '{"hook_event_name":"UserPromptSubmit","session_id":"demo-a","cwd":"'"$PWD"'","prompt":"Crie o dashboard companion em Compose Desktop"}' "A: prompt"
hook '{"hook_event_name":"PreToolUse","session_id":"demo-a","cwd":"'"$PWD"'","tool_name":"Grep","tool_input":{"pattern":"StateFlow"}}' "A: explorando"
event '{"agent":"claude-code","session_id":"demo-b","status":"CODING","repository":"tonimadev/api-pagamentos","branch":"feature/pix","message":"Implementando webhook do PIX","target":"src/pix/WebhookController.kt"}' "B: codando (via /events)"
hook '{"hook_event_name":"PreToolUse","session_id":"demo-a","cwd":"'"$PWD"'","tool_name":"Edit","tool_input":{"file_path":"'"$PWD"'/desktopApp/src/main/kotlin/dev/kommander/app/Main.kt"}}' "A: editando"
hook '{"hook_event_name":"PreToolUse","session_id":"demo-a","cwd":"'"$PWD"'","tool_name":"Bash","tool_input":{"command":"./gradlew test","description":"Run unit tests"}}' "A: testando"
event '{"agent":"claude-code","session_id":"demo-b","status":"DEPLOYING","repository":"tonimadev/api-pagamentos","branch":"feature/pix","target":"Magalu Cloud","message":"Subindo os containers..."}' "B: deploy"
hook '{"hook_event_name":"PreToolUse","session_id":"demo-a","cwd":"'"$PWD"'","tool_name":"mcp__github__create_pull_request","tool_input":{"owner":"tonimadev","repo":"Kommander","title":"Companion dashboard em Compose Desktop"}}' "A: abrindo PR"
hook '{"hook_event_name":"Notification","session_id":"demo-a","cwd":"'"$PWD"'","message":"Claude needs your permission to use Bash"}' "A: aguardando você"
event '{"agent":"claude-code","session_id":"demo-b","status":"DONE","repository":"tonimadev/api-pagamentos","branch":"feature/pix","target":"Magalu Cloud","message":"Deploy concluído"}' "B: concluído"
hook '{"hook_event_name":"Stop","session_id":"demo-a","cwd":"'"$PWD"'"}' "A: terminou"
