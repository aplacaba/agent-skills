#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# openspec-tooling setup for opencode
# Installs the canonical skills/commands/agents/plugin into the global opencode
# config (~/.config/opencode) and merges the Neo4j MCP server block.
#
# Safe to re-run: symlinks are refreshed, the config merge is idempotent, and
# a backup of opencode.json(.c) is written before any edit.
# ---------------------------------------------------------------------------

# Allow overriding the global opencode config dir for testing.
OPENCODE_CONFIG_DIR="${OPENCODE_CONFIG_DIR:-$HOME/.config/opencode}"

say()  { printf '%s\n' "$*"; }
warn() { printf 'WARN: %s\n' "$*" >&2; }
err()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. Prerequisite checks
# ---------------------------------------------------------------------------
say "==> Checking prerequisites"

missing_hard=0
check_cmd() {
  local tool="$1"; shift
  if command -v "$tool" >/dev/null 2>&1; then
    say "  [ok] $tool"
    return 0
  fi
  warn "  [missing] $tool — $*"
  return 1
}

check_cmd bb     "install babashka (https://babashka.org)" || missing_hard=1
check_cmd openspec "install via: npm i -g openspec" || missing_hard=1
check_cmd docker  "install Docker (https://docs.docker.com/get-docker/)" || missing_hard=1
check_cmd git     "install git (https://git-scm.com)" || missing_hard=1

if [ "$missing_hard" -eq 1 ]; then
  err "hard prerequisites are missing; install them and re-run ./setup.sh"
fi

# Resolve the repo root portably (BSD vs GNU readlink differ; use babashka).
# Done after the prerequisite gate so a missing bb produces the install hint
# instead of failing under `set -e`.
REPO_ROOT="$(bb -e '(require (quote [babashka.fs :as fs])) (println (str (fs/canonicalize (first *command-line-args*))))' "$(dirname "${BASH_SOURCE[0]}")")"

# ---------------------------------------------------------------------------
# 2. Idempotent symlinking into the global opencode config
# ---------------------------------------------------------------------------
say "==> Installing into $OPENCODE_CONFIG_DIR"

mkdir -p \
  "$OPENCODE_CONFIG_DIR/skill" \
  "$OPENCODE_CONFIG_DIR/command" \
  "$OPENCODE_CONFIG_DIR/agent" \
  "$OPENCODE_CONFIG_DIR/plugins"

# Create or refresh a symlink at $link -> $target.
# Refuses to touch an existing real file/dir (not a symlink).
symlink() {
  local target="$1"
  local link="$2"
  if [ -e "$link" ] && [ ! -L "$link" ]; then
    warn "  skip $link (real file/dir exists; remove it to let setup manage it)"
    return
  fi
  ln -sfn "$target" "$link"
  say "  link $link -> $target"
}

# Skills (one directory per skill, contains SKILL.md)
for d in "$REPO_ROOT"/skills/*/; do
  [ -d "$d" ] || continue
  symlink "$d" "$OPENCODE_CONFIG_DIR/skill/$(basename "$d")"
done

# Commands (one .md file per command)
for f in "$REPO_ROOT"/commands/*.md; do
  [ -e "$f" ] || continue
  symlink "$f" "$OPENCODE_CONFIG_DIR/command/$(basename "$f")"
done

# Agent
symlink "$REPO_ROOT/agents/openspec-reviewer.md" \
        "$OPENCODE_CONFIG_DIR/agent/openspec-reviewer.md"

# opencode plugin (injects skills.paths)
symlink "$REPO_ROOT/.opencode/plugins/openspec-tooling.js" \
        "$OPENCODE_CONFIG_DIR/plugins/openspec-tooling.js"

# ---------------------------------------------------------------------------
# 3. Neo4j MCP config merge (idempotent, with backup)
# ---------------------------------------------------------------------------
say "==> Merging Neo4j MCP config"

if [ -z "${NEO4J_PASSWORD:-}" ]; then
  warn "NEO4J_PASSWORD not set — skipping Neo4j MCP merge (set it and re-run)"
else
  NEO4J_URI="${NEO4J_URI:-bolt://localhost:7687}" \
  NEO4J_USER="${NEO4J_USER:-neo4j}" \
  NEO4J_PASSWORD="$NEO4J_PASSWORD" \
  OPENCODE_CONFIG_DIR="$OPENCODE_CONFIG_DIR" \
  bb "$REPO_ROOT/scripts/config-merge.clj"
fi

# ---------------------------------------------------------------------------
# 4. Per-harness install guidance
# ---------------------------------------------------------------------------
cat <<EOF

==> opencode is installed. Restart opencode to pick up the changes.

==> Other harnesses (install separately):

  Claude Code (inside a session):
    /plugin marketplace add $REPO_ROOT
    /plugin install openspec-tooling@openspec-tooling-dev

  Codex (inside the CLI):
    /plugins
    openspec-tooling
    Install Plugin

  See docs/install.md for details and the Neo4j MCP env vars.
EOF
