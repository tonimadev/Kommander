#!/usr/bin/env bash
# Mescla os hooks do Kommander em ~/.claude/settings.json (escopo de usuário: vale
# para todas as sessões do Claude Code, em qualquer repositório).
# Faz backup do arquivo antes. Requer `jq`.
set -euo pipefail

SETTINGS="${CLAUDE_SETTINGS:-$HOME/.claude/settings.json}"
HOOKS="$(cd "$(dirname "$0")" && pwd)/hooks.json"

command -v jq >/dev/null || { echo "Instale o jq (ex.: sudo apt install jq) ou copie $HOOKS manualmente." >&2; exit 1; }

mkdir -p "$(dirname "$SETTINGS")"
[ -f "$SETTINGS" ] || echo '{}' > "$SETTINGS"
cp "$SETTINGS" "$SETTINGS.bak.$(date +%s)"

# Para cada evento, acrescenta nossos hooks sem apagar os que já existem (e sem duplicar).
jq --slurpfile k "$HOOKS" '
  .hooks //= {} |
  reduce ($k[0].hooks | to_entries[]) as $e (.;
    .hooks[$e.key] = (((.hooks[$e.key] // []) + $e.value) | unique))
' "$SETTINGS" > "$SETTINGS.tmp" && mv "$SETTINGS.tmp" "$SETTINGS"

echo "Hooks do Kommander instalados em $SETTINGS"
