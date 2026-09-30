#!/usr/bin/env bash
# Simulates two Claude Code sessions working on different repositories, plus events
# from your own automation script via /events.
# Session A shows the dynamic flow: tests fail, Claude fixes the code and runs them again;
# what it "says" between tools comes from a temporary JSONL transcript.
set -euo pipefail
BASE="${KOMMANDER_URL:-http://127.0.0.1:8080}"
DELAY="${DELAY:-1.5}"
TRANSCRIPT="$(mktemp --suffix=.jsonl)"
trap 'rm -f "$TRANSCRIPT"' EXIT

hook() { curl -s -o /dev/null -w "%{http_code} " -H 'Content-Type: application/json' -d "$1" "$BASE/hooks/claude-code"; echo "$2"; sleep "$DELAY"; }
event() { curl -s -o /dev/null -w "%{http_code} " -H 'Content-Type: application/json' -d "$1" "$BASE/events"; echo "$2"; sleep "$DELAY"; }
# Fields shared by session A.
A='"session_id":"demo-a","cwd":"'"$PWD"'","transcript_path":"'"$TRANSCRIPT"'"'
# Claude "speaks" in the transcript before using the next tool.
say() { printf '{"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"%s"}]}}\n' "$1" >> "$TRANSCRIPT"; }
prompt() { printf '{"type":"user","message":{"role":"user","content":"%s"}}\n' "$1" >> "$TRANSCRIPT"; }

hook '{"hook_event_name":"SessionStart",'"$A"',"source":"startup"}' "A: session started (current repo)"
prompt "Fix the dashboard repository filter"
hook '{"hook_event_name":"UserPromptSubmit",'"$A"',"prompt":"Fix the dashboard repository filter"}' "A: prompt"
say "Let me find where the repository filter is applied."
hook '{"hook_event_name":"PreToolUse",'"$A"',"tool_name":"Grep","tool_use_id":"a1","tool_input":{"pattern":"repositoryFilter"}}' "A: exploring"
hook '{"hook_event_name":"PostToolUse",'"$A"',"tool_name":"Grep","tool_use_id":"a1","tool_input":{"pattern":"repositoryFilter"},"tool_response":{"numFiles":3}}' "A: grep ok"
event '{"agent":"claude-code","session_id":"demo-b","status":"CODING","repository":"acme/payments-api","branch":"feature/webhooks","message":"Implementing the payment webhook","target":"src/webhooks/WebhookController.kt"}' "B: coding (via /events)"
say "Found it: the filter is not reset when the last session of the repository goes away. Fixing it in the reducer."
hook '{"hook_event_name":"PreToolUse",'"$A"',"tool_name":"Edit","tool_use_id":"a2","tool_input":{"file_path":"'"$PWD"'/presentation/src/main/kotlin/dev/kommander/presentation/dashboard/DashboardReducer.kt"}}' "A: editing"
hook '{"hook_event_name":"PostToolUse",'"$A"',"tool_name":"Edit","tool_use_id":"a2","tool_input":{"file_path":"DashboardReducer.kt"},"tool_response":{"filePath":"DashboardReducer.kt"}}' "A: edit ok"
say "Running the tests to confirm."
hook '{"hook_event_name":"PreToolUse",'"$A"',"tool_name":"Bash","tool_use_id":"a3","tool_input":{"command":"./gradlew :presentation:test","description":"Run presentation tests"}}' "A: testing"
hook '{"hook_event_name":"PostToolUseFailure",'"$A"',"tool_name":"Bash","tool_use_id":"a3","tool_input":{"command":"./gradlew :presentation:test"},"error":"Exit code 1\n> Task :presentation:test FAILED\n24 tests completed, 1 failed","is_interrupt":false}' "A: tests FAILED"
event '{"agent":"claude-code","session_id":"demo-b","status":"DEPLOYING","repository":"acme/payments-api","branch":"feature/webhooks","target":"production cluster","message":"Rolling out the new release..."}' "B: deploying"
say "An old test expected the filter to be kept. Updating it to the new behavior."
hook '{"hook_event_name":"PreToolUse",'"$A"',"tool_name":"Edit","tool_use_id":"a4","tool_input":{"file_path":"'"$PWD"'/presentation/src/test/kotlin/dev/kommander/presentation/dashboard/DashboardReducerTest.kt"}}' "A: fixing the test"
hook '{"hook_event_name":"PostToolUse",'"$A"',"tool_name":"Edit","tool_use_id":"a4","tool_input":{},"tool_response":{}}' "A: edit ok"
hook '{"hook_event_name":"PreToolUse",'"$A"',"tool_name":"Bash","tool_use_id":"a5","tool_input":{"command":"./gradlew :presentation:test","description":"Run presentation tests"}}' "A: testing again"
hook '{"hook_event_name":"PostToolUse",'"$A"',"tool_name":"Bash","tool_use_id":"a5","tool_input":{"command":"./gradlew :presentation:test"},"tool_response":{"stdout":"BUILD SUCCESSFUL in 4s","stderr":"","interrupted":false}}' "A: tests passed"
say "Tests are green. Letting the team know and opening the PR."
hook '{"hook_event_name":"PreToolUse",'"$A"',"tool_name":"mcp__chat__send_message","tool_use_id":"a6","mcp_server":"chat","tool_input":{"channel":"#dev","text":"Filter fix is ready"}}' "A: generic MCP tool"
hook '{"hook_event_name":"PreToolUse",'"$A"',"tool_name":"mcp__github__create_pull_request","tool_use_id":"a7","tool_input":{"owner":"tonimadev","repo":"Kommander","title":"Reset the filter when its repository goes away"}}' "A: opening PR"
hook '{"hook_event_name":"Notification",'"$A"',"message":"Claude needs your permission to use GitHub"}' "A: waiting for you"
hook '{"hook_event_name":"PostToolUse",'"$A"',"tool_name":"mcp__github__create_pull_request","tool_use_id":"a7","tool_input":{},"tool_response":[{"type":"text","text":"PR #12 created"}]}' "A: PR opened (you approved)"
event '{"agent":"claude-code","session_id":"demo-b","status":"DONE","repository":"acme/payments-api","branch":"feature/webhooks","target":"production cluster","message":"Release rolled out"}' "B: done"
hook '{"hook_event_name":"Stop",'"$A"',"last_assistant_message":"Fixed the repository filter: it now falls back to **all** when the last session of that repo is removed. Tests pass and PR #12 is open."}' "A: finished"
