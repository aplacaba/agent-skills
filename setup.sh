#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# openspec-tooling setup for opencode
# Installs the canonical skills/commands/agents/plugin into the global opencode
# config (~/.config/opencode).
#
# Safe to re-run: symlinks are refreshed.
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
# 3. Story-graph vault guidance
# ---------------------------------------------------------------------------
say "==> Story-graph vault"

VAULT="${OBSIDIAN_VAULT:-$HOME/obsidian/obsidian}"
if [ ! -d "$VAULT" ]; then
  warn "vault not found at $VAULT — story-driven apply will fail until you"
  warn "  create it or set OBSIDIAN_VAULT to an existing vault"
else
  say "  [ok] vault: $VAULT"
fi

# ---------------------------------------------------------------------------
# 4. Per-harness install guidance
# ---------------------------------------------------------------------------
cat <<EOF

==> opencode is installed. Restart opencode to pick up the changes.

==> Story graph storage:
  The story-driven workflow stores stories as markdown notes in an Obsidian
  vault (OBSIDIAN_VAULT, default $HOME/obsidian/obsidian). No database or MCP
  server is required; an Obsidian MCP server may be registered for read access.

==> Other harnesses (install separately):

  Claude Code (inside a session):
    /plugin marketplace add $REPO_ROOT
    /plugin install openspec-tooling@openspec-tooling-dev

  Codex (inside the CLI):
    /plugins
    openspec-tooling
    Install Plugin

  See docs/install.md for details.
EOF

if command -v pi >/dev/null 2>&1; then
  cat <<EOF

  Pi (pi installed — skip if already added; check with \`pi list\`):
    pi install $REPO_ROOT
    pi install https://github.com/aplacaba/agent-skills.git   # git source

  Skills are discovered by convention from the repo's skills/ directory; no
  Pi adapter files are needed. See docs/install.md.
EOF
fi
